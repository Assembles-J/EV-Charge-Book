package com.evchargebook.ui.dashboard

import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

@Composable
fun DashboardScreen() {
    Card {
        Text(
            text = "EV Charge Book\n\n零跑 C16\n\n本月电费 ¥0\n累计里程 0 km",
            fontSize = 20.sp
        )
    }
}
