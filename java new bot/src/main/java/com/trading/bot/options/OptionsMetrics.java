package com.trading.bot.options;

public class OptionsMetrics {
    private final double optionPrice;
    private final double impliedVolatility;
    private final double delta;
    private final double gamma;
    private final double theta;
    private final double vega;
    private final double rho;

    public OptionsMetrics(double optionPrice, double impliedVolatility,
                         double delta, double gamma, double theta, double vega, double rho) {
        this.optionPrice = optionPrice;
        this.impliedVolatility = impliedVolatility;
        this.delta = delta;
        this.gamma = gamma;
        this.theta = theta;
        this.vega = vega;
        this.rho = rho;
    }

    public double getOptionPrice() {
        return optionPrice;
    }

    public double getImpliedVolatility() {
        return impliedVolatility;
    }

    public double getDelta() {
        return delta;
    }

    public double getGamma() {
        return gamma;
    }

    public double getTheta() {
        return theta;
    }

    public double getVega() {
        return vega;
    }

    public double getRho() {
        return rho;
    }
}