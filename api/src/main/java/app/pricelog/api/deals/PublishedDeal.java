package app.pricelog.api.deals;

import java.util.List;

/**
 * One promotion as read from a retailer's public listing, before it is matched
 * against anything logged.
 *
 * <p>The two money fields are separate on purpose. Costco prints the promotional
 * price and the amount taken off as different things ("$23.99" then "After $6
 * OFF"), and confusing one for the other turns a $6 saving into a $23 one.
 *
 * @param itemNumbers   retailer item numbers the offer covers; an offer can span
 *                      several, e.g. a jacket and matching pant
 * @param salePriceCents what the item costs during the promotion
 * @param discountCents  how much is being taken off
 * @param inWarehouse    false for online-only offers
 */
public record PublishedDeal(
        List<String> itemNumbers,
        String title,
        Integer salePriceCents,
        Integer discountCents,
        boolean inWarehouse) {

    /** True when at least one of the two money fields was readable. */
    public boolean hasPricing() {
        return salePriceCents != null || discountCents != null;
    }
}
