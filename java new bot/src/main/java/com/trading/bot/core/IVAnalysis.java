public class IVAnalysis {
    private final double impliedVolatility;
    private final double historicalVolatility;
    private final boolean isFavorable;
    
    public IVAnalysis(double impliedVolatility, double historicalVolatility) {
        this.impliedVolatility = impliedVolatility;
        this.historicalVolatility = historicalVolatility;
        this.isFavorable = analyzeVolatility();
    }
    
    private boolean analyzeVolatility() {
        // IV is favorable when it's higher than historical for selling
        // and lower than historical for buying
        return Math.abs(impliedVolatility - historicalVolatility) > 0.05;
    }
    
    public boolean isFavorable() {
        return isFavorable;
    }
    
    public double getImpliedVolatility() {
        return impliedVolatility;
    }
    
    public double getHistoricalVolatility() {
        return historicalVolatility;
    }
}