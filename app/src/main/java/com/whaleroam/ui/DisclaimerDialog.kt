package com.whaleroam.ui

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.whaleroam.R

/**
 * v1.5.0 免责声明弹窗（勾选式 + 逐条编号条款）。
 *
 * 两种互不相干的形态，务必分开使用：
 *  - [showForce]  首次启动强制态：未勾选时「同意」不可点；点「不同意」、
 *                按返回键或点对话框外部 → 一律 finish() 退出应用。
 *  - [showReadOnly] 只读态（抽屉入口 / 设置页）：展示同样条款，但无需勾选，
 *                只提供「关闭」按钮，关闭弹窗即返回原页面，
 *                完全不触碰 SettingsStore.disclaimerAccepted。
 */
object DisclaimerDialog {

    /** 「同意」按钮未勾选时的透明度（视觉上明确不可点） */
    private const val AGREE_DISABLED_ALPHA = 0.4f

    /** 首次启动强制态：必须勾选后才能同意，否则退出应用 */
    fun showForce(activity: Activity, onAccepted: () -> Unit) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_disclaimer, null)
        val checkRow = view.findViewById<View>(R.id.disclaimerCheckRow)
        val checkBox = view.findViewById<MaterialCheckBox>(R.id.disclaimerCheckBox)
        val checkLabel = view.findViewById<View>(R.id.disclaimerCheckLabel)
        val agreeBtn = view.findViewById<MaterialButton>(R.id.disclaimerAgreeBtn)
        val declineBtn = view.findViewById<MaterialButton>(R.id.disclaimerDeclineBtn)
        val closeBtn = view.findViewById<MaterialButton>(R.id.disclaimerCloseBtn)

        // 强制态：显示勾选行与「不同意 / 同意」，隐藏「关闭」
        checkRow.visibility = View.VISIBLE
        declineBtn.visibility = View.VISIBLE
        agreeBtn.visibility = View.VISIBLE
        closeBtn.visibility = View.GONE

        // 「同意」默认不可点，并降低透明度，避免用户误以为可点
        agreeBtn.isEnabled = false
        agreeBtn.alpha = AGREE_DISABLED_ALPHA

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            // 禁止返回键与点击外部关闭：未同意不得进入主界面
            .setCancelable(false)
            .setOnCancelListener { activity.finish() }
            .create()

        // 勾选后才允许同意
        val syncAgreeState = {
            val ok = checkBox.isChecked
            agreeBtn.isEnabled = ok
            agreeBtn.alpha = if (ok) 1f else AGREE_DISABLED_ALPHA
        }
        checkBox.setOnCheckedChangeListener { _, _ -> syncAgreeState() }
        checkLabel.setOnClickListener { checkBox.toggle() }
        syncAgreeState()

        agreeBtn.setOnClickListener {
            if (!agreeBtn.isEnabled) return@setOnClickListener
            onAccepted()
            dialog.dismiss()
        }
        // 不同意 → 退出应用
        declineBtn.setOnClickListener {
            dialog.dismiss()
            activity.finish()
        }

        dialog.show()
    }

    /** 只读态（抽屉 / 设置页入口）：无需勾选，点「关闭」即可返回，不影响已同意状态 */
    fun showReadOnly(activity: Activity) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_disclaimer, null)
        val checkRow = view.findViewById<View>(R.id.disclaimerCheckRow)
        val agreeBtn = view.findViewById<MaterialButton>(R.id.disclaimerAgreeBtn)
        val declineBtn = view.findViewById<MaterialButton>(R.id.disclaimerDeclineBtn)
        val closeBtn = view.findViewById<MaterialButton>(R.id.disclaimerCloseBtn)

        // 只读态：隐藏勾选行与「同意 / 不同意」，只显示「关闭」
        checkRow.visibility = View.GONE
        agreeBtn.visibility = View.GONE
        declineBtn.visibility = View.GONE
        closeBtn.visibility = View.VISIBLE

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .create()

        // 只读态关闭只关弹窗，绝不 finish()，也不写 disclaimerAccepted
        closeBtn.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
