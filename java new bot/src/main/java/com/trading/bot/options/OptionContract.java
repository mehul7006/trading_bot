package com.trading.bot.options;

import java.time.LocalDate;

public class OptionContract {
    private final String symbol;
    private final LocalDate expiryDate;
    private final double strikePrice;
    private final double premium;
    private final double openInterest;
    private final double impliedVolatility;

    public OptionContract(String symbol, LocalDate expiryDate, double strikePrice,
                         double premium, double openInterest, double impliedVolatility) {
        this.symbol = symbol;
        this.expiryDate = expiryDate;
        this.strikePrice = strikePrice;
        this.premium = premium;
        this.openInterest = openInterest;
        this.impliedVolatility = impliedVolatility;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public double getStrikePrice() {
        return strikePrice;
    }

    public double getPremium() {
        return premium;
    }

    public double getOpenInterest() {
        return openInterest;
    }

    public double getImpliedVolatility() {
        return impliedVolatility;
    }
}