package top.sacz.timtool.hook.item.chat

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import top.sacz.timtool.hook.base.BaseSwitchFunctionHookItem
import top.sacz.timtool.hook.core.annotation.HookItem
import top.sacz.timtool.hook.util.ToastTool

@HookItem("辅助功能/聊天/TIM群标题栏添加精华入口")
class TimBarAddEssence : BaseSwitchFunctionHookItem() {

    override fun loadHook(loader: ClassLoader) {
        try {
            val timRight1VBClass = loader.loadClass("com.tencent.tim.aio.titlebar.TimRight1VB")
            val redDotClass = loader.loadClass("com.tencent.mobileqq.aio.widget.RedDotImageView")

            val targetMethod = timRight1VBClass.declaredMethods.find {
                it.returnType == redDotClass
            } ?: return

            val iconId = View.generateViewId()

            hookAfter(targetMethod) { param ->
                val redDotView = param.result as? View ?: return@hookAfter
                val rootView = redDotView.parent as? ViewGroup ?: return@hookAfter

                if (rootView.findViewById<View>(iconId) != null) return@hookAfter

                val context = rootView.context

                val iconView = ImageView(context).apply {
                    layoutParams = RelativeLayout.LayoutParams(
                        dp2px(context, 20f),
                        dp2px(context, 20f)
                    ).apply {
                        addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                        addRule(RelativeLayout.CENTER_VERTICAL)
                        marginEnd = dp2px(context, 70f)
                    }
                    id = iconId
                    val iconResId = context.resources.getIdentifier("qui_tui_brand_products", "drawable", context.packageName)
                    if (iconResId != 0) {
                        setImageResource(iconResId)
                    } else {
                        setImageResource(android.R.drawable.star_on)
                    }
                    val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    setColorFilter(if (nightMode == Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK)
                }

                iconView.setOnClickListener {
                    val troopUin = getTroopUin(param.thisObject)
                    if (troopUin == null) {
                        ToastTool.error("无法获取群号")
                        return@setOnClickListener
                    }
                    try {
                        val browserClass = loader.loadClass("com.tencent.mobileqq.activity.QQBrowserDelegationActivity")
                        val intent = Intent(context, browserClass).apply {
                            putExtra("fling_action_key", 2)
                            putExtra("fling_code_key", context.hashCode())
                            putExtra("useDefBackText", true)
                            putExtra("param_force_internal_browser", true)
                            putExtra("url", "https://qun.qq.com/essence/index?gc=$troopUin")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        ToastTool.error("无法启动内置浏览器")
                    }
                }

                rootView.addView(iconView)
            }
        } catch (e: Exception) {
            // 忽略初始化失败（静默失效）
        }
    }

    private fun getTroopUin(instance: Any): String? {
        // 优先尝试字段
        runCatching {
            val fields = instance.javaClass.declaredFields
            for (field in fields) {
                field.isAccessible = true
                when (field.name) {
                    "peerUid" -> return field.get(instance) as? String
                    "troopUin" -> return field.get(instance)?.toString()
                }
            }
        }
        // 尝试 getter 方法
        runCatching {
            val methods = instance.javaClass.methods
            for (method in methods) {
                if (method.parameterCount == 0) {
                    when (method.name) {
                        "getPeerUid" -> return method.invoke(instance) as? String
                        "getTroopUin" -> return method.invoke(instance)?.toString()
                    }
                }
            }
        }
        return null
    }

    private fun dp2px(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
