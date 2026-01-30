#!/bin/bash
cd "$(dirname "$0")"
CLASSPATH=".:lib/*:target/classes"
java -cp "$CLASSPATH" com.trading.bot.core.WorkingTradingBot "$@"
