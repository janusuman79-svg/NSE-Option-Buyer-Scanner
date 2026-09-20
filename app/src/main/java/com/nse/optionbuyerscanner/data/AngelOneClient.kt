package com.nse.optionbuyerscanner.data
/** Build 1 broker boundary. SmartAPI authentication/candle calls are intentionally isolated here.
 * Do not commit credentials. Wire current Angel One endpoints after validating your SmartAPI account/API version.
 */
class AngelOneClient { suspend fun validateConfiguration(s:AppSettings)=s.angelApiKey.isNotBlank()&&s.clientCode.isNotBlank()&&s.pin.isNotBlank()&&s.totpSecret.isNotBlank() }
