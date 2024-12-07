package com.aleatory.websocketsrouting.domain;

import java.time.LocalDate;

public class ClosePrice {

	LocalDate closeDate;
	Double price;
	boolean finalPrice;
	
	public ClosePrice(LocalDate closeDate, Double price) {
		super();
		this.closeDate = closeDate;
		this.price = price;
	}

	public LocalDate getCloseDate() {
		return closeDate;
	}
	public Double getPrice() {
		return price;
	}
	@Override
	public String toString() {
		return "ClosePrice [closeDate=" + closeDate + ", price=" + price + "]";
	}

	public boolean isFinalPrice() {
		return finalPrice;
	}

	public void setFinalPrice(boolean finalPrice) {
		this.finalPrice = finalPrice;
	}
	
	
}
