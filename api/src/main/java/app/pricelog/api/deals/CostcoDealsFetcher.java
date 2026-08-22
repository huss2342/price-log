package app.pricelog.api.deals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads Costco's own published warehouse savings listing.
 *
 * <p>Costco offers no API, so this parses their public promotions page. That
 * makes it inherently brittle: a redesign will stop it matching, and it must
 * fail quietly rather than break the rest of the app. It is deliberately read
 * at most a couple of times a day, on demand.
 *
 * <p>The page renders each offer as a run of lines like:
 * <pre>
 *   adidas Men's Quarter Sock     &lt;- title
 *   Warehouse                     &lt;- where the offer is valid
 *   &amp;
 *   Online
 *   adidas Men's Quarter Sock     &lt;- title again
 *   6 pair
 *   Item 1927653
 *   Limit 10. Selection varies by location.
 *   $
 *   9                             &lt;- discount, split across two lines
 * </pre>
 */
@Component
public class CostcoDealsFetcher {

    private static final Logger log = LoggerFactory.getLogger(CostcoDealsFetcher.class);

    private static final Pattern ITEM_LINE = Pattern.compile("^Item\\s*([\\d,\\s]+)$");
    private static final Pattern ITEM_NUMBER = Pattern.compile("\\d{5,9}");
    private static final Pattern DOLLARS_OFF =
            Pattern.compile("\\$\\s*([\\d,]+(?:\\.\\d{2})?)\\s*OFF", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_AMOUNT = Pattern.compile("^[\\d,]+(\\.\\d{2})?$");
    private static final Pattern SIZE_ONLY =
            Pattern.compile("^[\\d.,\\s]+(ct|oz|lb|pair|pairs|pk|pack|count)?$", Pattern.CASE_INSENSITIVE);

    /** Layout words that are never a product name. */
    private static final Set<String> MARKERS = Set.of(
            "warehouse", "online", "&", "buy online", "while supplies last",
            "shop now", "add to cart", "buy in warehouse", "new lower price");

    private final RestClient client;
    private final String url;

    public CostcoDealsFetcher(
            @Value("${pricelog.deals.costco-url:https://www.costco.com/o/-/warehouse-savings}") String url) {
        this.url = url;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofSeconds(45));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public List<PublishedDeal> fetch() {
        String html = client.get()
                .uri(url)
                // Costco serves a different page, or nothing, without a browser
                // user agent.
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .retrieve()
                .body(String.class);

        if (html == null || html.isBlank()) {
            throw new IllegalStateException("Costco returned an empty page.");
        }
        return parse(html);
    }

    /** Exposed for testing against a saved copy of the page. */
    public List<PublishedDeal> parse(String html) {
        List<String> lines = textLines(html);
        List<PublishedDeal> deals = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            Matcher item = ITEM_LINE.matcher(lines.get(i));
            if (!item.matches()) {
                continue;
            }

            List<String> itemNumbers = itemNumbers(item.group(1));
            if (itemNumbers.isEmpty()) {
                continue;
            }

            List<String> before = lines.subList(Math.max(0, i - 8), i);
            List<String> after = lines.subList(i + 1, Math.min(lines.size(), i + 9));

            String title = title(before);
            Integer discount = discountCents(after);
            if (title == null || discount == null) {
                continue;
            }

            boolean inWarehouse = before.stream().anyMatch(l -> l.equalsIgnoreCase("warehouse"));
            deals.add(new PublishedDeal(itemNumbers, title, discount, inWarehouse));
        }

        log.info("Parsed {} Costco deals ({} valid in warehouse)",
                deals.size(), deals.stream().filter(PublishedDeal::inWarehouse).count());
        return deals;
    }

    /**
     * The product name appears twice in each block, once above the availability
     * markers and once below, while descriptions and sizes appear only once.
     * Preferring a repeated line avoids picking up "Limit 10" or "10 pairs".
     */
    private String title(List<String> before) {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (String line : before) {
            if (isCandidateTitle(line)) {
                counts.merge(line, 1L, Long::sum);
            }
        }

        String repeated = counts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .reduce((first, second) -> second)
                .orElse(null);
        if (repeated != null) {
            return repeated;
        }

        // Nothing repeated: fall back to the last plausible line.
        for (int i = before.size() - 1; i >= 0; i--) {
            if (isCandidateTitle(before.get(i))) {
                return before.get(i);
            }
        }
        return null;
    }

    private boolean isCandidateTitle(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return line.length() >= 6
                && !MARKERS.contains(lower)
                && !SIZE_ONLY.matcher(line).matches()
                && !lower.startsWith("limit ")
                && !lower.startsWith("includes ")
                && !lower.startsWith("selection varies");
    }

    private Integer discountCents(List<String> after) {
        for (int i = 0; i < after.size(); i++) {
            String line = after.get(i);

            Matcher off = DOLLARS_OFF.matcher(line);
            if (off.find()) {
                return toCents(off.group(1));
            }
            // The page also renders the amount as a lone "$" then the number.
            if (line.equals("$") && i + 1 < after.size() && BARE_AMOUNT.matcher(after.get(i + 1)).matches()) {
                return toCents(after.get(i + 1));
            }
        }
        return null;
    }

    private Integer toCents(String amount) {
        try {
            return new java.math.BigDecimal(amount.replace(",", ""))
                    .movePointRight(2)
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .intValueExact();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<String> itemNumbers(String raw) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = ITEM_NUMBER.matcher(raw);
        while (m.find()) {
            found.add(m.group());
        }
        return List.copyOf(found);
    }

    /** Strips scripts, styles and tags, leaving one trimmed line per text node. */
    private List<String> textLines(String html) {
        String stripped = html
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", "\n");

        List<String> lines = new ArrayList<>();
        for (String raw : stripped.split("\n")) {
            String line = unescape(raw).trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");

    private String unescape(String text) {
        String result = text
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&rsquo;", "'")
                .replace("&lsquo;", "'")
                .replace("&ldquo;", "\"")
                .replace("&rdquo;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">");

        // Costco writes apostrophes as &#x27;, so numeric entities have to be
        // decoded too or every possessive product name keeps the raw entity.
        Matcher m = NUMERIC_ENTITY.matcher(result);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int codePoint = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            m.appendReplacement(out, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
        }
        m.appendTail(out);

        // Ampersand last, so a literal "&amp;#x27;" cannot turn into an entity.
        return out.toString().replace("&amp;", "&");
    }
}
