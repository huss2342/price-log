package app.pricelog.api.capture;

import app.pricelog.api.domain.PriceObservation;
import app.pricelog.api.extract.ExtractedTag;
import app.pricelog.api.extract.TagVerdict;

/** Everything the capture screen needs to show a result and let you correct it. */
public record CaptureResult(PriceObservation observation, TagVerdict verdict, ExtractedTag tag) {
}
