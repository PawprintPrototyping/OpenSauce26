package org.pawprint.gachapaw.service

import android.content.Context
import android.content.Intent
import com.squareup.sdk.pos.ChargeRequest
import com.squareup.sdk.pos.CurrencyCode
import com.squareup.sdk.pos.PosClient
import com.squareup.sdk.pos.PosSdk
import org.pawprint.gachapaw.BuildConfig
import java.util.concurrent.TimeUnit

class SquarePaymentRepository(context: Context) {
    private val posClient: PosClient = PosSdk.createClient(context, BuildConfig.SQUARE_APPLICATION_ID)

    fun createChargeIntent(
        amountCents: Int,
        currencyCode: CurrencyCode = CurrencyCode.USD,
        note: String,
        returnTimeout: Long,
        timeUnit: TimeUnit = TimeUnit.MILLISECONDS,
    ): Intent {
        return posClient.createChargeIntent(
            ChargeRequest.Builder(amountCents, currencyCode)
                .autoReturn(returnTimeout, timeUnit)
                .note(note)
                .build()
        )
    }

    fun parseChargeSuccess(data: Intent) = posClient.parseChargeSuccess(data)
    fun parseChargeError(data: Intent) = posClient.parseChargeError(data)
}
