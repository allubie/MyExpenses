package org.totschnig.webui

import android.app.Notification
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.annotation.StringRes
import androidx.core.database.getLongOrNull
import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.commons.text.StringSubstitutor
import org.apache.commons.text.StringSubstitutor.*
import org.apache.commons.text.lookup.StringLookup
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.di.LocalDateAdapter
import org.totschnig.myexpenses.di.LocalTimeAdapter
import org.totschnig.myexpenses.feature.IWebInputService
import org.totschnig.myexpenses.feature.START_ACTION
import org.totschnig.myexpenses.feature.STOP_ACTION
import org.totschnig.myexpenses.feature.ServerStateObserver
import org.totschnig.myexpenses.feature.WebUiBinder
import org.totschnig.myexpenses.model.CurrencyContext
import org.totschnig.myexpenses.model2.Transaction
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.DatabaseConstants.*
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.asSequence
import org.totschnig.myexpenses.ui.ContextHelper
import org.totschnig.myexpenses.util.NotificationBuilderWrapper
import org.totschnig.myexpenses.util.NotificationBuilderWrapper.NOTIFICATION_WEB_UI
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.io.getWifiIpAddress
import org.totschnig.myexpenses.util.locale.UserLocaleProvider
import java.io.IOException
import java.net.ServerSocket
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

private const val STOP_CLICK_ACTION = "STOP_CLICK_ACTION"

class WebInputService : Service(), IWebInputService {

    @Inject
    lateinit var repository: Repository

    @Inject
    lateinit var gson: Gson

    @Inject
    lateinit var prefHandler: PrefHandler

    @Inject
    lateinit var userLocaleProvider: UserLocaleProvider

    @Inject
    lateinit var currencyContext: CurrencyContext

    private lateinit var wrappedContext: Context

    private val binder = LocalBinder()

    private var serverStateObserver: ServerStateObserver? = null

    private var count = 0

    private var port: Int = 0

    inner class LocalBinder : WebUiBinder() {
        override fun getService() = this@WebInputService
    }

    override fun onCreate() {
        super.onCreate()
        DaggerWebUiComponent.builder().appComponent((application as MyApplication).appComponent)
            .build().inject(this)
        wrappedContext = ContextHelper.wrap(this, userLocaleProvider.getUserPreferredLocale())
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private var server: ApplicationEngine? = null

    private val serverAddress: String?
        get() = if (server != null) address else null

    override fun registerObserver(serverStateObserver: ServerStateObserver) {
        this.serverStateObserver = serverStateObserver
        serverAddress?.let { serverStateObserver.postAddress(it) }
    }

    override fun unregisterObserver() {
        this.serverStateObserver = null
    }

    private val address: String
        get() = "http://${getWifiIpAddress(this)}:$port"


    private fun readTextFromAssets(fileName: String) = assets.open(fileName).bufferedReader()
        .use {
            it.readText()
        }

    private fun readBytesFromAssets(fileName: String) = assets.open(fileName).use {
        it.readBytes()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            STOP_CLICK_ACTION -> {
                prefHandler.putBoolean(PrefKey.UI_WEB, false)
            }
            STOP_ACTION -> {
                if (stopServer()) {
                    serverStateObserver?.onStopped()
                }
            }
            START_ACTION -> {
                if (server != null) {
                    stopServer()
                }
                if (try {
                        (9000..9050).first { isAvailable(it) }
                    } catch (e: NoSuchElementException) {
                        serverStateObserver?.postException(IOException("No available port found in range 9000..9050"))
                        0
                    }.let { port = it; it != 0 }
                ) {
                    server = embeddedServer(CIO, port, watchPaths = emptyList()) {
                        install(ContentNegotiation) {
                            gson {
                                registerTypeAdapter(
                                    LocalDate::class.java,
                                    LocalDateAdapter
                                )
                                registerTypeAdapter(
                                    LocalTime::class.java,
                                    LocalTimeAdapter
                                )
                            }
                        }
                        val passWord = prefHandler.getString(PrefKey.WEBUI_PASSWORD, "")
                            .takeIf { !it.isNullOrBlank() }
                        passWord?.let {
                            install(Authentication) {
                                basic("auth-basic") {
                                    realm = getString(R.string.app_name)
                                    validate { credentials ->
                                        if (credentials.password == it) {
                                            UserIdPrincipal(credentials.name)
                                        } else {
                                            null
                                        }
                                    }
                                }
                            }
                        }

                        install(StatusPages) {
                            exception<Throwable> { call, cause ->
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    "Internal Server Error"
                                )
                                CrashHandler.report(cause)
                                throw cause
                            }
                        }

                        routing {

                            get("/styles.css") {
                                call.respondText(
                                    readTextFromAssets("styles.css"),
                                    ContentType.Text.CSS
                                )
                            }
                            get("/favicon.ico") {
                                call.respondBytes(
                                    readBytesFromAssets("favicon.ico"),
                                    ContentType.Image.XIcon
                                )
                            }
                            if (passWord == null) {
                                serve()
                            } else {
                                authenticate("auth-basic") {
                                    serve()
                                }
                            }
                        }
                    }.also {
                        it.start(wait = false)
                    }

                    val stopIntent = Intent(this, WebInputService::class.java).apply {
                        action = STOP_CLICK_ACTION
                    }
                    val notification: Notification =
                        NotificationBuilderWrapper.defaultBigTextStyleBuilder(
                            this,
                            getString(R.string.title_webui),
                            address
                        )
                            .addAction(
                                0,
                                0,
                                getString(R.string.stop),
                                //noinspection InlinedApi
                                PendingIntent.getService(
                                    this,
                                    0,
                                    stopIntent,
                                    FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                                )
                            )
                            .build()

                    startForeground(NOTIFICATION_WEB_UI, notification)
                    serverStateObserver?.postAddress(address)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun Route.serve() {
        post("/") {
            val transaction = call.receive<Transaction>()
            val success = if (transaction.id == 0L) {
                repository.createTransaction(transaction) != null
            } else {
                repository.updateTransaction(transaction) == 1
            }
            if (success) {
                count++
                call.respond(
                    HttpStatusCode.Created,
                    "${getString(R.string.save_transaction_and_new_success)} ($count)"
                )
            } else {
                call.respond(
                    HttpStatusCode.Conflict,
                    "Error while saving transaction."
                )
            }
        }
        get("/") {
            val categories = contentResolver.query(
                TransactionProvider.CATEGORIES_URI.buildUpon()
                    .appendQueryParameter(
                        TransactionProvider.QUERY_PARAMETER_HIERARCHICAL,
                        "1"
                    ).build(),
                arrayOf(KEY_ROWID, KEY_PARENTID, KEY_LABEL, KEY_LEVEL),
                null, null, null
            )?.use { cursor ->
                cursor.asSequence.map {
                    mapOf(
                        "id" to it.getLong(0),
                        "parent" to it.getLongOrNull(1),
                        "label" to it.getString(2),
                        "level" to it.getInt(3)
                    )
                }
                    .toList()
            }
            val data = mapOf(
                "accounts" to contentResolver.query(
                    TransactionProvider.ACCOUNTS_BASE_URI,
                    arrayOf(KEY_ROWID, KEY_LABEL, KEY_TYPE, KEY_CURRENCY),
                    "$KEY_SEALED = 0", null, null
                )?.use { cursor ->
                    cursor.asSequence.map {
                        mapOf(
                            "id" to it.getLong(0),
                            "label" to it.getString(1),
                            "type" to it.getString(2),
                            "currency" to currencyContext[it.getString(3)].symbol
                        )
                    }.toList()
                },
                "payees" to contentResolver.query(
                    TransactionProvider.PAYEES_URI,
                    arrayOf(KEY_ROWID, KEY_PAYEE_NAME),
                    null, null, null
                )?.use { cursor ->
                    cursor.asSequence.map {
                        mapOf(
                            "id" to it.getLong(0),
                            "name" to it.getString(1)
                        )
                    }
                        .toList()
                },
                "categories" to categories,
                "tags" to contentResolver.query(
                    TransactionProvider.TAGS_URI,
                    arrayOf(KEY_ROWID, KEY_LABEL),
                    null, null, null
                )?.use { cursor ->
                    cursor.asSequence.map {
                        mapOf(
                            "id" to it.getLong(0),
                            "label" to it.getString(1)
                        )
                    }
                        .toList()
                },
                "methods" to contentResolver.query(
                    TransactionProvider.METHODS_URI,
                    arrayOf(
                        KEY_ROWID,
                        KEY_LABEL,
                        KEY_IS_NUMBERED,
                        KEY_TYPE,
                        KEY_ACCOUNT_TPYE_LIST
                    ),
                    null, null, null
                )?.use { cursor ->
                    cursor.asSequence.map {
                        mapOf(
                            "id" to it.getLong(0),
                            "label" to it.getString(1),
                            "isNumbered" to (it.getInt(2) > 0),
                            "type" to it.getInt(3),
                            "accountTypes" to it.getString(4)?.split(',')
                        )
                    }.toList()
                },
            )
            val categoryTreeDepth =
                categories?.map { it["level"] as Int }?.maxOrNull() ?: 0
            val categoryWatchers = if (categoryTreeDepth > 1) {
                (0..categoryTreeDepth - 2).joinToString(separator = "\n") {
                    "\$watch('categoryPath[$it].id', value => { categoryPath[${it + 1}].id=0 } );"
                }
            } else ""
            val lookup = StringLookup { key ->
                when (key) {
                    "i18n_title" -> "${t(R.string.app_name)} ${getString(R.string.title_webui)}"
                    "i18n_account" -> t(R.string.account)
                    "i18n_amount" -> t(R.string.amount)
                    "i18n_date" -> t(R.string.date)
                    "i18n_time" -> t(R.string.time)
                    "i18n_booking_date" -> t(R.string.booking_date)
                    "i18n_value_date" -> t(R.string.value_date)
                    "i18n_payee" -> t(R.string.payer_or_payee)
                    "i18n_category" -> t(R.string.category)
                    "i18n_tags" -> t(R.string.tags)
                    "i18n_notes" -> t(R.string.comment)
                    "i18n_method" -> t(R.string.method)
                    "i18n_submit" -> t(R.string.menu_save)
                    "i18n_create_transaction" -> t(R.string.menu_create_transaction)
                    "i18n_edit_transaction" -> t(R.string.menu_edit_transaction)
                    "i18n_number" -> t(R.string.reference_number)
                    "i18n_edit" -> t(R.string.menu_edit)
                    "i18n_discard_changes" -> t(R.string.dialog_confirm_discard_changes)
                    "category_tree_depth" -> categoryTreeDepth.toString()
                    "data" -> gson.toJson(data)
                    "categoryWatchers" -> categoryWatchers
                    "withValueDate" -> prefHandler.getBoolean(
                        PrefKey.TRANSACTION_WITH_VALUE_DATE,
                        false
                    ).toString()
                    "withTime" -> prefHandler.getBoolean(
                        PrefKey.TRANSACTION_WITH_TIME,
                        false
                    ).toString()
                    else -> throw IllegalStateException("Unknown substitution key $key")
                }
            }
            val stringSubstitutor = StringSubstitutor(
                lookup,
                DEFAULT_PREFIX,
                DEFAULT_SUFFIX,
                DEFAULT_ESCAPE
            )
            val text =
                stringSubstitutor.replace(readTextFromAssets("form.html"))
            call.respondText(text, ContentType.Text.Html)
        }
        get("/accounts/{id}") {
            call.respond(repository.loadTransactions(call.parameters["id"]!!.toLong()))
        }
    }

    private fun isAvailable(portNr: Int) = try {
        ServerSocket(portNr).let {
            it.close()
            true
        }
    } catch (e: IOException) {
        false
    }

    private fun t(@StringRes resId: Int) = wrappedContext.getString(resId)

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun stopServer() = if (server != null) {
        server?.stop(0, 0)
        server = null
        stopForeground(true)
        true
    } else false
}