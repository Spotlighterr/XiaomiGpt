package wtf.mahdi.glassesconnect.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.StateFlow
import wtf.mahdi.glassesconnect.BuildConfig
import wtf.mahdi.glassesconnect.GlassesController
import wtf.mahdi.glassesconnect.R
import wtf.mahdi.glassesconnect.ui.CollectMessages
import wtf.mahdi.glassesconnect.ui.ControllerViewModel
import wtf.mahdi.glassesconnect.ui.LocalController
import wtf.mahdi.glassesconnect.ui.LocalNav
import wtf.mahdi.glassesconnect.ui.Route
import wtf.mahdi.glassesconnect.ui.components.AppBar
import wtf.mahdi.glassesconnect.ui.components.ListGroup
import wtf.mahdi.glassesconnect.ui.components.ListRow
import wtf.mahdi.glassesconnect.ui.components.SectionLabel
import wtf.mahdi.glassesconnect.ui.theme.AppLang
import wtf.mahdi.glassesconnect.ui.theme.LocalStrings
import wtf.mahdi.glassesconnect.ui.theme.Xiao

data class MoreState(val ready: Boolean, val connecting: Boolean, val state: String, val signedIn: Boolean, val user: String,
                     val hasKey: Boolean, val logLines: Int, val hasToken: Boolean)

class MoreViewModel(c: GlassesController) : ControllerViewModel(c) {
    val state: StateFlow<MoreState> = derive {
        MoreState(it.ready, it.connecting, it.state, it.cloud.signedIn, it.cloud.user, it.aiSettings.key.isNotBlank(), it.log.size, it.token.isNotBlank())
    }
    fun toggleConnection() = if (state.value.ready || state.value.connecting) { c.disconnect(); message("Disconnected") } else c.connect()
}

@Composable
fun MoreScreen() {
    val controller = LocalController.current
    val vm = viewModel { MoreViewModel(controller) }
    CollectMessages(vm)
    val st by vm.state.collectAsStateWithLifecycle()
    val acc by controller.accounts.state.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    val nav = LocalNav.current

    Column(Modifier.fillMaxSize()) {
        AppBar(s.moreTitle)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(s.sectionGlasses)
            ListGroup {
                row { ListRow(s.glassesSettings, s.glassesSettingsSub, R.drawable.ic_tune, onClick = { nav.go(Route.Settings) }) }
                row { ListRow(s.liveStream, s.liveStreamSub, R.drawable.ic_sensors, onClick = { nav.go(Route.Stream) }) }
            }
            SectionLabel(s.sectionApp)
            ListGroup {
                row {
                    val nextLang = if (acc.appLang == AppLang.VI) AppLang.EN else AppLang.VI
                    ListRow(s.appLanguage, acc.appLang.label, R.drawable.ic_tune, onClick = { controller.accounts.appLang(nextLang) })
                }
                row {
                    ListRow(s.connection, if (st.signedIn) "Xiaomi ${st.user}" else if (st.hasToken) s.keySaved else s.notSetUp,
                        R.drawable.ic_link, onClick = { nav.go(Route.Onboarding()) })
                }
                row {
                    ListRow(if (st.ready || st.connecting) s.disconnect else s.connect, st.state, R.drawable.ic_bluetooth,
                        enabled = st.hasToken, onClick = vm::toggleConnection, trailing = {})
                }
                row { ListRow(s.aiAssistantRow, s.aiAssistantRowSub, R.drawable.ic_auto_awesome, onClick = { nav.go(Route.AiSettings) }) }
            }
            SectionLabel(s.sectionDeveloper)
            ListGroup {
                row { ListRow(s.devLog, "${st.logLines} lines", R.drawable.ic_receipt_long, onClick = { nav.go(Route.Developer(0)) }) }
                row { ListRow(s.devCommands, s.devCommandsSub, R.drawable.ic_terminal, onClick = { nav.go(Route.Developer(1)) }) }
                row { ListRow(s.devRoadmap, s.devRoadmapSub, R.drawable.ic_checklist, onClick = { nav.go(Route.Developer(2)) }) }
            }
            Text("XiaomiGpt Link ${BuildConfig.VERSION_NAME} · XiaoLink GPL-3.0 + XiaomiGpt MIT", Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = Xiao.colors.muted, textAlign = TextAlign.Center)
        }
    }
}
