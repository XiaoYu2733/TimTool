package top.sacz.timtool.hook.item.file

import android.os.Environment
import top.sacz.timtool.hook.HookEnv
import top.sacz.timtool.hook.base.BaseSwitchFunctionHookItem
import top.sacz.timtool.hook.core.annotation.HookItem
import top.sacz.timtool.hook.util.LogUtils
import top.sacz.xphelper.util.ConfigUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

@HookItem("辅助功能/实验性/每天自动备份模块数据")
class AutoBackupModuleData : BaseSwitchFunctionHookItem() {

    companion object {
        private const val TAG = "AutoBackupModuleData"
        private const val CONFIG_NAME = "AutoBackup"
    }

    private val config = ConfigUtils(CONFIG_NAME)
    private var isProcessing = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    // 动态获取模块数据目录 /storage/emulated/0/Android/data/com.tencent.tim/Tim小助手
    private val moduleSourceDir: String by lazy {
        val context = HookEnv.getHostAppContext()
        val androidDataDir = context.getExternalFilesDir(null)?.parentFile
        if (androidDataDir != null) {
            File(androidDataDir, "Tim小助手").absolutePath
        } else {
            // 使用外部存储根目录拼接
            "${Environment.getExternalStorageDirectory().absolutePath}/Android/data/com.tencent.tim/Tim小助手"
        }
    }

    // 动态获取备份目录 /storage/emulated/0/Download/TimTool
    private val backupTargetDir: String by lazy {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        File(downloadDir, "TimTool").absolutePath
    }

    override fun getTip(): String {
        return "每天启动TIM自动备份模块数据"
    }

    override fun isLoadedByDefault(): Boolean {
        return true
    }

    override fun loadHook(classLoader: ClassLoader) {
        Thread {
            try {
                if (isProcessing) return@Thread
                isProcessing = true

                val today = dateFormat.format(Date())
                val lastBackupDate = config.getString("last_backup_date", "")

                try {
                    ensureNoMediaFile()
                    if (today != lastBackupDate) {
                        deleteAllZipFiles()
                        performBackup()
                        config.put("last_backup_date", today)
                    }
                } catch (e: Exception) {
                    LogUtils.addError(TAG, "自动备份执行异常", e)
                }
            } catch (e: Exception) {
                LogUtils.addError(TAG, "启动备份检查异常", e)
            } finally {
                isProcessing = false
            }
        }.start()
    }

    private fun ensureNoMediaFile() {
        val backupDir = File(backupTargetDir)
        if (!backupDir.exists()) {
            backupDir.mkdirs()
            File(backupDir, ".nomedia").createNewFile()
        } else if (backupDir.isDirectory) {
            val noMedia = File(backupDir, ".nomedia")
            if (!noMedia.exists()) noMedia.createNewFile()
        } else {
            throw IOException("备份路径不是目录: $backupTargetDir")
        }
    }

    private fun performBackup(): File {
        val sourceDir = File(moduleSourceDir)
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            throw IOException("模块数据目录无效: $moduleSourceDir")
        }

        val timestamp = timestampFormat.format(Date())
        val backupFile = File(backupTargetDir, "TimToolBackup_$timestamp.zip")

        FileOutputStream(backupFile).use { fos ->
            java.util.zip.ZipOutputStream(fos).use { zos ->
                zipDirectory(sourceDir, sourceDir.name, zos)
            }
        }
        return backupFile
    }

    private fun zipDirectory(source: File, entryName: String, zos: java.util.zip.ZipOutputStream) {
        source.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                zipDirectory(file, "$entryName/${file.name}", zos)
            } else {
                FileInputStream(file).use { fis ->
                    zos.putNextEntry(java.util.zip.ZipEntry("$entryName/${file.name}"))
                    fis.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
    }

    private fun deleteAllZipFiles() {
        val backupDir = File(backupTargetDir)
        if (backupDir.exists() && backupDir.isDirectory) {
            backupDir.listFiles()?.forEach {
                if (it.isFile && it.name.endsWith(".zip", ignoreCase = true)) {
                    it.delete()
                }
            }
        }
    }
}
