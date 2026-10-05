package com.ciao.backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name="lost_item_claims")
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class LostItemClaim {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer id;
    public Integer getId() { return id; }
    public void setId(Integer value) { id = value; }
    @ManyToOne @JoinColumn(name="item_id", nullable=false)
    private LostItem item;
    public LostItem getItem() { return item; }
    public void setItem(LostItem value) { item = value; }
    @ManyToOne @JoinColumn(name="claimant_id", nullable=false)
    private User claimant;
    public User getClaimant() { return claimant; }
    public void setClaimant(User value) { claimant = value; }
    @ManyToOne @JoinColumn(name="handled_by_id")
    private StaffProfile handledBy;
    public StaffProfile getHandledBy() { return handledBy; }
    public void setHandledBy(StaffProfile value) { handledBy = value; }
    @Column(nullable=false,length=2000)
    private String proofOfOwnership;
    public String getProofOfOwnership() { return proofOfOwnership; }
    public void setProofOfOwnership(String value) { proofOfOwnership = value; }
    
    private java.time.LocalDateTime claimDate;
    public java.time.LocalDateTime getClaimDate() { return claimDate; }
    public void setClaimDate(java.time.LocalDateTime value) { claimDate = value; }
    
    private java.time.LocalDateTime returnedAt;
    public java.time.LocalDateTime getReturnedAt() { return returnedAt; }
    public void setReturnedAt(java.time.LocalDateTime value) { returnedAt = value; }
    
    private String claimStatus;
    public String getClaimStatus() { return claimStatus; }
    public void setClaimStatus(String value) { claimStatus = value; }
}

