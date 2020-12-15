package org.totschnig.myexpenses.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NotNull
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.activity.BaseActivity
import org.totschnig.myexpenses.feature.OcrFeature
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.AppDirHelper
import org.totschnig.myexpenses.util.PictureDirHelper
import java.io.File
import javax.inject.Inject

class OcrViewModel(application: Application) : AndroidViewModel(application) {

    init {
        (application as MyApplication).appComponent.inject(this)
    }

    @Inject
    lateinit var prefHandler: PrefHandler

    val ocrFeature: OcrFeature
        get() = getApplication<MyApplication>().appComponent.ocrFeature() ?: throw IllegalStateException()

    fun tessDataExists() = liveData(context = viewModelScope.coroutineContext + Dispatchers.IO) {
        emit(ocrFeature.isAvailable(getApplication<MyApplication>()))
    }

    fun downloadTessData() = liveData(context = viewModelScope.coroutineContext + Dispatchers.IO) {
        emit(ocrFeature.downloadTessData(getApplication(), prefHandler))
    }

    fun startOcrFeature(scanFile: File, fragmentManager: FragmentManager) {
        ocrFeature.start(scanFile, fragmentManager)
    }

    fun handleOcrData(intent: Intent?, fragmentManager: FragmentManager) {
        ocrFeature.handleData(intent, fragmentManager)
    }

    fun getScanFiles(action: (file: Pair<File, File>) -> Unit) {
        viewModelScope.launch {
            action(withContext(Dispatchers.IO) {
                Pair(PictureDirHelper.getOutputMediaFile("SCAN", true, false), PictureDirHelper.getOutputMediaFile("SCAN_CROPPED", true, false))
            })
        }
    }

    fun getScanUri(file: File): Uri = try {
        AppDirHelper.getContentUriForFile(file)
    }  catch (e: IllegalArgumentException) {
        Uri.fromFile(file)
    }

    fun offerTessDataDownload(baseActivity: BaseActivity) {
        ocrFeature.offerInstall(baseActivity)
    }
}