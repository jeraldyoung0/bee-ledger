package com.beeledger.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.beeledger.app.Bus
import com.beeledger.app.data.Tx

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BeeTheme {
                Root()
            }
        }
    }
}

@Composable
fun Root() {
    var tab by remember { mutableStateOf(0) }
    var route by remember { mutableStateOf("main") }
    var editing by remember { mutableStateOf<Tx?>(null) }
    val ver by Bus.version.collectAsState()

    BackHandler(enabled = route != "main") {
        route = "main"
        editing = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (route == "main") {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TabItem(0, "📋", "明细", tab) { tab = it }
                    TabItem(1, "📊", "统计", tab) { tab = it }
                    TabItem(2, "⚙️", "我的", tab) { tab = it }
                }
            }
        },
        floatingActionButton = {
            if (route == "main" && tab == 0) {
                FloatingActionButton(
                    onClick = {
                        editing = null
                        route = "add"
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Text("＋", fontSize = 26.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { pad ->
        Box(modifier = Modifier.fillMaxSize().padding(pad)) {
            when (route) {
                "add" -> AddScreen(
                    editing = editing,
                    onDone = {
                        route = "main"
                        editing = null
                    }
                )

                "log" -> LogScreen(onBack = { route = "main" })

                "rules" -> RulesScreen(onBack = { route = "main" })

                else -> when (tab) {
                    0 -> HomeScreen(
                        ver = ver,
                        onEdit = {
                            editing = it
                            route = "add"
                        }
                    )

                    1 -> StatsScreen(ver = ver)

                    else -> MineScreen(
                        ver = ver,
                        onOpenLog = { route = "log" },
                        onOpenRules = { route = "rules" }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(
    index: Int,
    icon: String,
    label: String,
    current: Int,
    onClick: (Int) -> Unit,
) {
    NavigationBarItem(
        selected = current == index,
        onClick = { onClick(index) },
        icon = { Text(icon, fontSize = 16.sp) },
        label = { Text(label, fontSize = 11.sp) }
    )
}
