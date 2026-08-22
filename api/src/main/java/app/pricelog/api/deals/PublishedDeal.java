package app.pricelog.api.deals;

import java.util.List;

/**
 * One promotion as read from a retailer's public listing, before it is matched
 * against anything logged.
 *
 * @param itemNumbers retailer item numbers the offer covers; an offer can span
 *                    several, e.g. a jacket and matching pant
 * @param inWarehouse false for online-only offers
 */
public record PublishedDeal(
        List<String> itemNumbers,
        String title,
        Integer discountCents,
        boolean inWarehouse) {
}
