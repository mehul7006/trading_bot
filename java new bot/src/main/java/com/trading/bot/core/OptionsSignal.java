public class OptionsSignal {
    public final String symbol;
    public final String optionType;
    public final double strikePrice;
    public final double entryPrice;
    public final double target1;
    public final double target2;
    public final double stopLoss;
    public final double confidence;
    
    public OptionsSignal(String symbol, String optionType, double strikePrice,
                        double entryPrice, double target1, double target2,
                        double stopLoss, double confidence) {
        this.symbol = symbol;
        this.optionType = optionType;
        this.strikePrice = strikePrice;
        this.entryPrice = entryPrice;
        this.target1 = target1;
        this.target2 = target2;
        this.stopLoss = stopLoss;
        this.confidence = confidence;
    }
    
    @Override
    public String toString() {
        return String.format("""
            🎯 OPTIONS TRADE SIGNAL
            Symbol: %s
            Type: %s
            Strike: %.2f
            Entry: %.2f
            Target 1: %.2f
            Target 2: %.2f
            Stop Loss: %.2f
            Confidence: %.1f%%
            """,
            symbol, optionType, strikePrice, entryPrice,
            target1, target2, stopLoss, confidence);
    }
}