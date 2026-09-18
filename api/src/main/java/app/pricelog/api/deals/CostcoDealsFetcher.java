package app.pricelog.api.deals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * <p>The page renders each offer as a run of text nodes, in one of two shapes:
 * <pre>
 *   Amylu Paleo Andouille Chicken Sausages   &lt;- title
 *   Warehouse Only                           &lt;- where the offer is valid
 *   Amylu Paleo Andouille Chicken Sausages   &lt;- title again
 *   40 oz
 *   Item 1451835
 *   Save                                     &lt;- what follows is the saving...
 *   $
 *   4
 *
 *   Pulmuone Teriyaki Stir-Fry Udon
 *   Warehouse                                &lt;- or "Warehouse", "&amp;", "Online"
 *   &amp;
 *   Online
 *   Pulmuone Teriyaki Stir-Fry Udon
 *   Item 2062082
 *   Valid through 8/30/26.                   &lt;- only when it ends early
 *   $                                        &lt;- ...while this is the price
 *   5
 *   .
 *   99
 *   After $2.70 OFF
 * </pre>
 * The whole booklet's run is printed once, as "Valid 8/24/26 - 9/20/26".
 */
@Component
public class CostcoDealsFetcher {

    private static final Logger log = LoggerFactory.getLogger(CostcoDealsFetcher.class);

    private static final Pattern ITEM_LINE = Pattern.compile("^Item\\s*([\\d,\\s]+)$");
    private static final Pattern ITEM_NUMBER = Pattern.compile("\\d{5,9}");
    private static final Pattern DOLLARS_OFF =
            Pattern.compile("\\$\\s*([\\d,]+(?:\\.\\d{2})?)\\s*OFF", Pattern.CASE_INSENSITIVE);
    /** Leading amount of a run like "$23.99" or "$9", once the fragments are joined. */
    private static final Pattern LEADING_PRICE =
            Pattern.compile("^\\$\\s*([\\d,]+(?:\\.\\d{1,2})?)");
    /** The digits, point and cents of a price each arrive as their own text node. */
    private static final Pattern PRICE_FRAGMENT = Pattern.compile("^[\\d.,]+$");
    private static final Pattern SIZE_ONLY =
            Pattern.compile("^[\\d.,\\s]+(ct|oz|lb|pair|pairs|pk|pack|count)?$", Pattern.CASE_INSENSITIVE);
    /** "2/16.5 oz", "18/12 fl oz": a pack size, never a product name. */
    private static final Pattern PACK_SIZE = Pattern.compile("^\\d+/\\d");
    private static final Pattern BOOKLET_VALIDITY = Pattern.compile(
            "Valid\\s+\\d{1,2}/\\d{1,2}/\\d{2,4}\\s*-\\s*(\\d{1,2}/\\d{1,2}/\\d{2,4})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OFFER_VALIDITY = Pattern.compile(
            "^Valid\\s+(?:through|thru)\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})", Pattern.CASE_INSENSITIVE);

    /** Layout words that are never a product name. */
    private static final Set<String> MARKERS = Set.of(
            "warehouse", "online", "&", "buy online", "while supplies last", "warehouse only",
            "online only", "shop now", "add to cart", "buy in warehouse", "new lower price", "save");

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
        LocalDate bookletEnds = bookletEnd(lines);
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

            List<String> before = ownBlock(lines.subList(Math.max(0, i - 8), i));
            List<String> after = lines.subList(i + 1, Math.min(lines.size(), i + 9));

            String title = title(before);
            Terms terms = terms(after);
            if (title == null || !terms.any()) {
                continue;
            }

            // "Warehouse Only" and "Warehouse", "&", "Online" both count; only
            // "Online Only" offers cannot be picked up on a trip.
            boolean inWarehouse = before.stream()
                    .anyMatch(l -> l.toLowerCase(Locale.ROOT).startsWith("warehouse"));
            deals.add(new PublishedDeal(itemNumbers, title, terms.priceCents(), terms.discountCents(),
                    inWarehouse, terms.validUntil() == null ? bookletEnds : terms.validUntil()));
        }

        log.info("Parsed {} Costco deals ({} valid in warehouse)",
                deals.size(), deals.stream().filter(PublishedDeal::inWarehouse).count());
        return deals;
    }

    /**
     * The lines above an item number can reach back into the previous offer, whose
     * "Warehouse" marker would otherwise make an online-only offer look valid in
     * the warehouse. Everything up to the previous item number belongs to that one.
     */
    private List<String> ownBlock(List<String> before) {
        for (int i = before.size() - 1; i >= 0; i--) {
            if (ITEM_LINE.matcher(before.get(i)).matches()) {
                return before.subList(i + 1, before.size());
            }
        }
        return before;
    }

    private LocalDate bookletEnd(List<String> lines) {
        for (String line : lines) {
            Matcher m = BOOKLET_VALIDITY.matcher(line);
            if (m.find()) {
                return parseDate(m.group(1));
            }
        }
        return null;
    }

    /**
     * The product name appears twice in each block, once above the availability
     * markers and once below, while descriptions and sizes appear only once.
     * Preferring a repeated line avoids picking up "Limit 10" or "10 pairs".
     */
    private String title(List<String> before) {
        Map<String, Long> counts = new LinkedHashMap<>();
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
                && !PACK_SIZE.matcher(line).find()
                && !DOLLARS_OFF.matcher(line).find()
                && !line.startsWith("$")
                && !lower.startsWith("limit ")
                && !lower.startsWith("valid ")
                && !lower.startsWith("includes ")
                && !lower.startsWith("selection varies");
    }

    private record Terms(Integer priceCents, Integer discountCents, LocalDate validUntil) {
        boolean any() {
            return priceCents != null || discountCents != null;
        }
    }

    /**
     * Reads the promotional price, the amount off and an early end date. The
     * page prints the money three ways:
     * <pre>
     *   $ 23 . 99        the price during the promotion, split across lines
     *   After $6 OFF     the amount taken off
     *   Save $ 4         also the amount taken off, with no price at all
     * </pre>
     * Treating a saving as a price would advertise sausages at $4 that ring up
     * at $9.99, so each is parsed independently and any may be absent.
     */
    private Terms terms(List<String> after) {
        Integer price = null;
        Integer discount = null;
        LocalDate validUntil = null;

        for (int i = 0; i < after.size(); i++) {
            String line = after.get(i);
            if (startsNextOffer(line)) {
                // Past here the figures belong to the next offer on the page.
                break;
            }

            Matcher valid = OFFER_VALIDITY.matcher(line);
            if (valid.find()) {
                validUntil = parseDate(valid.group(1));
                continue;
            }

            // Checked first: "After $6 OFF" also contains a dollar amount.
            Matcher off = DOLLARS_OFF.matcher(line);
            if (off.find()) {
                if (discount == null) {
                    discount = toCents(off.group(1));
                }
                continue;
            }

            if (!line.startsWith("$")) {
                continue;
            }
            Matcher leading = LEADING_PRICE.matcher(joinPriceFragments(after, i));
            if (!leading.find()) {
                continue;
            }
            String previous = i == 0 ? "" : after.get(i - 1);
            if (previous.equalsIgnoreCase("save")) {
                if (discount == null) {
                    discount = toCents(leading.group(1));
                }
            } else if (previous.equals("-")) {
                // The top of a "Save $100 - $1,200" range, which is not a price.
                continue;
            } else if (price == null) {
                price = toCents(leading.group(1));
            }
        }
        return new Terms(price, discount, validUntil);
    }

    private boolean startsNextOffer(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return ITEM_LINE.matcher(line).matches()
                || lower.startsWith("warehouse")
                || lower.equals("online")
                || lower.equals("online only")
                || lower.equals("buy online");
    }

    /** "$", "1", ",", "200" reads as "$1,200", and stops before a following size or title. */
    private String joinPriceFragments(List<String> lines, int start) {
        StringBuilder joined = new StringBuilder(lines.get(start));
        for (int i = start + 1; i < Math.min(lines.size(), start + 5); i++) {
            if (!PRICE_FRAGMENT.matcher(lines.get(i)).matches()) {
                break;
            }
            joined.append(lines.get(i));
        }
        return joined.toString();
    }

    /** "8/30/26" or "8/30/2026". Anything else is ignored rather than guessed at. */
    private LocalDate parseDate(String raw) {
        String[] parts = raw.split("/");
        if (parts.length != 3) {
            return null;
        }
        try {
            int year = Integer.parseInt(parts[2]);
            return LocalDate.of(year < 100 ? 2000 + year : year,
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    private Integer toCents(String amount) {
        try {
            return new BigDecimal(amount.replace(",", ""))
                    .movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP)
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
