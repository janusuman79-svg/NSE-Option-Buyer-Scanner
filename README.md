# NSE Option Buyer Scanner — Android Build 1

Native Android starter for an NSE F&O naked-option **signal/paper scanner**.

## Included
- Android/Jetpack Compose mobile dashboard
- 15-minute strategy core: EMA 13/48/200, VWAP, RSI, SMI, ATR and volume
- CALL/PUT signal model with underlying entry/invalidation and 1R/2R targets
- Foreground service shell
- Angel One settings boundary (credentials are not committed)
- Telegram alert client
- GitHub Actions debug-APK build workflow

## Important Build 1 limitation
`AngelOneClient.kt` is deliberately an integration boundary rather than guessed broker code. Angel One SmartAPI authentication, instrument-master retrieval, historical/intraday candle calls, current F&O universe filtering and option-contract selection must be wired against the currently documented SmartAPI endpoints and tested with a real account before this app is used for live signals.

The strategy engine is not a promise of profitability. Paper-test/backtest it including brokerage, taxes, spread and slippage before considering live trading.

## Cloud APK build
Push this project to GitHub and open **Actions → Build Android APK → Run workflow**. The debug APK will appear as a workflow artifact after a successful build.
