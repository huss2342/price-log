package app.pricelog.api.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "store")
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Chain chain;

    /** How you refer to this location, e.g. "Costco Tustin". */
    @Column(nullable = false, length = 128)
    private String label;

    @Column(length = 64)
    private String city;

    @Column(length = 8)
    private String state;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Store() {
    }

    public Store(Chain chain, String label, String city, String state) {
        this.chain = chain;
        this.label = label;
        this.city = city;
        this.state = state;
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

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
