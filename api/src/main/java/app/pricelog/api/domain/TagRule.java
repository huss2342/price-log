package app.pricelog.api.domain;

import jakarta.persistence.*;

/**
 * One store-specific price tag convention. Kept in the database rather than in
 * code so the heuristics can be corrected from the app as they are verified.
 */
@Entity
@Table(name = "tag_rule")
public class TagRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Chain chain;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 32)
    private MatchType matchType;

    @Column(nullable = false, length = 64)
    private String pattern;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SaleSignal signal;

    @Column(nullable = false, length = 255)
    private String meaning;

    @Column(length = 255)
    private String advice;

    /** Lower wins when several rules match the same tag. */
    @Column(nullable = false)
    private int priority = 100;

    @Column(nullable = false)
    private boolean enabled = true;

    protected TagRule() {
    }

    public Long getId() {
        return id;
    }

    public Chain getChain() {
        return chain;
    }

    public void setChain(Chain chain) {
        this.chain = chain;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public SaleSignal getSignal() {
        return signal;
    }

    public void setSignal(SaleSignal signal) {
        this.signal = signal;
    }

    public String getMeaning() {
        return meaning;
    }

    public void setMeaning(String meaning) {
        this.meaning = meaning;
    }

    public String getAdvice() {
        return advice;
    }

    public void setAdvice(String advice) {
        this.advice = advice;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
