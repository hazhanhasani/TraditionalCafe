package com.hazhanhasani.traditionalcafe

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private val cream = Color.rgb(250, 246, 238)
    private val brown = Color.rgb(78, 45, 24)
    private val turquoise = Color.rgb(14, 117, 120)
    private val card = Color.WHITE
    private val muted = Color.rgb(110, 101, 94)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = cream
        window.navigationBarColor = cream
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        setContentView(buildDashboard())
    }

    private fun buildDashboard(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(cream)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        root.addView(TextView(this).apply {
            text = "کافه سنتی"
            textSize = 28f
            setTextColor(brown)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.RIGHT
        })

        root.addView(TextView(this).apply {
            text = "مدیریت امروز"
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.RIGHT
            setPadding(0, dp(4), 0, dp(18))
        })

        val summary = GridLayout(this).apply {
            columnCount = 2
            rowCount = 2
            alignmentMode = GridLayout.ALIGN_MARGINS
            useDefaultMargins = true
        }

        summary.addView(statCard("فروش امروز", "۰ تومان"))
        summary.addView(statCard("قلیان امروز", "۰"))
        summary.addView(statCard("طلب دفتری", "۰ تومان"))
        summary.addView(statCard("صندوق فعلی", "۰ تومان"))
        root.addView(summary)

        root.addView(sectionTitle("دسترسی سریع"))

        val actions = listOf(
            "میزها" to "ثبت و مدیریت سفارش هر میز",
            "ثبت قلیان" to "ثبت سریع قلیان و طعم",
            "حساب دفتری" to "مشتریان بدهکار و پرداخت‌ها",
            "هزینه‌ها" to "ثبت هزینه روزانه",
            "تسویه" to "نقدی، کارت، کارت‌به‌کارت و ترکیبی",
            "گزارش‌ها" to "سود و زیان و عملکرد روزانه"
        )

        actions.forEach { (title, subtitle) ->
            root.addView(actionCard(title, subtitle))
        }

        root.addView(TextView(this).apply {
            text = "نسخه پایه • آماده اتصال به Cloudflare Worker"
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        })

        scroll.addView(root)
        return scroll
    }

    private fun statCard(title: String, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(card, 18f)
            elevation = dp(2).toFloat()
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }

            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 13f
                setTextColor(muted)
                gravity = Gravity.RIGHT
            })

            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 18f
                setTextColor(brown)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.RIGHT
                setPadding(0, dp(8), 0, 0)
            })
        }
    }

    private fun actionCard(title: String, subtitle: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(card, 16f)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }

            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 17f
                setTextColor(turquoise)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.RIGHT
            })

            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 13f
                setTextColor(muted)
                gravity = Gravity.RIGHT
                setPadding(0, dp(5), 0, 0)
            })
        }
    }

    private fun sectionTitle(textValue: String): View {
        return TextView(this).apply {
            text = textValue
            textSize = 18f
            setTextColor(brown)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.RIGHT
            setPadding(0, dp(24), 0, dp(4))
        }
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius.toInt()).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
