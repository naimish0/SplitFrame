package com.rameshta.splitframe.ads

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.rameshta.splitframe.BuildConfig
import com.rameshta.splitframe.R
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

@Composable
fun NativeAdvancedAd(
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color,
    supportingColor: Color,
    outlineColor: Color,
    primaryColor: Color,
    onPrimaryColor: Color,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val externalUiLauncher = LocalExternalUiLauncher.current
    val currentExternalUiLauncher = rememberUpdatedState(externalUiLauncher)
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    val adOwner = remember(context) { ReplaceableAdOwner<NativeAd>(NativeAd::destroy) }

    DisposableEffect(context, adOwner) {
        val loadGeneration = adOwner.beginLoad()
        val adLoader = AdLoader.Builder(context, BuildConfig.NATIVE_AD_UNIT_ID)
            .forNativeAd { ad ->
                if (adOwner.accept(loadGeneration, ad)) {
                    nativeAd = ad
                }
            }
            .withAdListener(
                object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        adOwner.failed(loadGeneration)
                        nativeAd = null
                    }

                    override fun onAdClicked() {
                        currentExternalUiLauncher.value.launch(ExternalUiReason.AdClick) {}
                    }
                },
            )
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
        onDispose {
            adOwner.dispose()
            nativeAd = null
        }
    }

    val ad = nativeAd ?: return
    val sponsoredDescription = stringResource(R.string.sponsored)
    val colors = NativeAdViewColors(
        container = containerColor.toArgb(),
        content = contentColor.toArgb(),
        supporting = supportingColor.toArgb(),
        outline = outlineColor.toArgb(),
        primary = primaryColor.toArgb(),
        onPrimary = onPrimaryColor.toArgb(),
    )
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = sponsoredDescription },
        factory = { createSplitFrameNativeAdView(it) },
        update = { view -> view.bind(ad, colors) },
        onRelease = NativeAdView::destroy,
    )
}

private data class NativeAdViewColors(
    val container: Int,
    val content: Int,
    val supporting: Int,
    val outline: Int,
    val primary: Int,
    val onPrimary: Int,
)

private data class NativeAdViewHolder(
    val icon: ImageView,
    val headline: TextView,
    val advertiser: TextView,
    val body: TextView,
    val media: MediaView,
    val callToAction: Button,
    val adBadge: TextView,
)

private fun createSplitFrameNativeAdView(context: Context): NativeAdView {
    val nativeAdView = NativeAdView(context)
    val content = LinearLayout(context)
    val icon = ImageView(context)
    val headline = TextView(context)
    val advertiser = TextView(context)
    val body = TextView(context)
    val media = MediaView(context)
    val callToAction = Button(context)
    val adChoices = AdChoicesView(context)
    val adBadge = TextView(context)

    val outer = FrameLayout(context)
    nativeAdView.addView(
        outer,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )

    content.orientation = LinearLayout.VERTICAL
    content.setPadding(context.dp(14), context.dp(14), context.dp(14), context.dp(14))
    outer.addView(
        content,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )
    outer.addView(
        adChoices,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END,
        ),
    )

    val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    content.addView(
        header,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )
    header.addView(
        icon,
        LinearLayout.LayoutParams(context.dp(48), context.dp(48)).apply {
            marginEnd = context.dp(12)
        },
    )

    val titleStack = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    header.addView(
        titleStack,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
    )
    titleStack.addView(headline)

    val metaRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    titleStack.addView(metaRow)
    metaRow.addView(adBadge)
    metaRow.addView(
        advertiser,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            marginStart = context.dp(8)
        },
    )

    content.addView(
        body,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = context.dp(10)
        },
    )
    content.addView(
        media,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            context.dp(132),
        ).apply {
            topMargin = context.dp(12)
        },
    )
    content.addView(
        callToAction,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.END
            topMargin = context.dp(12)
        },
    )

    nativeAdView.headlineView = headline
    nativeAdView.bodyView = body
    nativeAdView.iconView = icon
    nativeAdView.advertiserView = advertiser
    nativeAdView.mediaView = media
    nativeAdView.callToActionView = callToAction
    nativeAdView.adChoicesView = adChoices
    nativeAdView.tag = NativeAdViewHolder(
        icon = icon,
        headline = headline,
        advertiser = advertiser,
        body = body,
        media = media,
        callToAction = callToAction,
        adBadge = adBadge,
    )
    return nativeAdView
}

private fun NativeAdView.bind(ad: NativeAd, colors: NativeAdViewColors) {
    val holder = tag as NativeAdViewHolder
    background = GradientDrawable().apply {
        cornerRadius = context.dp(8).toFloat()
        setColor(colors.container)
        setStroke(context.dp(1), colors.outline)
    }
    holder.headline.apply {
        text = ad.headline
        setTextColor(colors.content)
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 2
    }
    holder.advertiser.apply {
        text = ad.advertiser.orEmpty()
        visibility = if (ad.advertiser.isNullOrBlank()) View.GONE else View.VISIBLE
        setTextColor(colors.supporting)
        textSize = 12f
        maxLines = 1
    }
    holder.adBadge.apply {
        text = context.getString(R.string.sponsored)
        setTextColor(colors.primary)
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
    }
    holder.body.apply {
        text = ad.body.orEmpty()
        visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
        setTextColor(colors.supporting)
        textSize = 14f
        maxLines = 3
    }
    holder.icon.apply {
        val drawable = ad.icon?.drawable
        visibility = if (drawable == null) View.GONE else View.VISIBLE
        setImageDrawable(drawable)
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
    }
    holder.media.apply {
        visibility = if (ad.mediaContent == null) View.GONE else View.VISIBLE
        mediaContent = ad.mediaContent
        setImageScaleType(ImageView.ScaleType.CENTER_CROP)
    }
    holder.callToAction.apply {
        text = ad.callToAction.orEmpty()
        visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
        setTextColor(colors.onPrimary)
        background = GradientDrawable().apply {
            cornerRadius = context.dp(6).toFloat()
            setColor(colors.primary)
        }
        minHeight = context.dp(48)
        minWidth = context.dp(96)
    }
    setNativeAd(ad)
}

private fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()
