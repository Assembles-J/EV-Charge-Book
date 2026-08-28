from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    return text.replace(old, new, 1)


# Main shell: let Dashboard draw behind the status bar, while preserving existing
# safe insets for every other tab/overlay. Also keep system icon contrast tied to
# the app theme rather than the phone's global night-mode setting.
main_path = Path("android/app/src/main/java/com/evchargebook/MainActivity.kt")
main = main_path.read_text(encoding="utf-8")
main = replace_once(main, "import android.Manifest\n", "import android.Manifest\nimport android.app.Activity\n", "MainActivity Activity import")
main = replace_once(main, "import androidx.core.content.ContextCompat\n", "import androidx.core.content.ContextCompat\nimport androidx.core.view.WindowCompat\n", "MainActivity WindowCompat import")
main = replace_once(main, "import com.evchargebook.ui.theme.EvChargeTheme\n", "import com.evchargebook.ui.theme.EvChargeTheme\nimport com.evchargebook.ui.theme.LocalAppThemeController\n", "MainActivity theme controller import")
main = replace_once(
    main,
    "    val hasOverlayPage = editVehicle || addVehicle || selectCatalogVehicle || bluetoothPrompt || addRecord || editingRecord != null || state.selectedTripId != null\n",
    "    val hasOverlayPage = editVehicle || addVehicle || selectCatalogVehicle || bluetoothPrompt || addRecord || editingRecord != null || state.selectedTripId != null\n"
    "    val themeController = LocalAppThemeController.current\n"
    "    SideEffect {\n"
    "        (context as? Activity)?.window?.let { window ->\n"
    "            WindowCompat.getInsetsController(window, window.decorView).apply {\n"
    "                // Dashboard is always visually dark because the Hero artwork reaches the system bar.\n"
    "                isAppearanceLightStatusBars = if (!hasOverlayPage && tab == 0) false else !themeController.darkTheme\n"
    "                isAppearanceLightNavigationBars = !themeController.darkTheme\n"
    "            }\n"
    "        }\n"
    "    }\n",
    "MainActivity system bar style",
)
main = replace_once(
    main,
    "    Scaffold(\n        containerColor = MaterialTheme.colorScheme.background,\n",
    "    Scaffold(\n"
    "        // Only the Dashboard owns the top system-bar backdrop. Other screens keep the\n"
    "        // existing Scaffold safe-drawing insets and therefore retain their current layout.\n"
    "        contentWindowInsets = if (!hasOverlayPage && tab == 0) {\n"
    "            WindowInsets(0, 0, 0, 0)\n"
    "        } else {\n"
    "            ScaffoldDefaults.contentWindowInsets\n"
    "        },\n"
    "        containerColor = MaterialTheme.colorScheme.background,\n",
    "MainActivity dashboard insets",
)
main_path.write_text(main, encoding="utf-8")


# Dashboard: remove the artificial 8dp strip above the Hero and tell the Hero it
# owns the edge-to-edge status-bar region.
dash_path = Path("android/app/src/main/java/com/evchargebook/ui/dashboard/DashboardScreen.kt")
dash = dash_path.read_text(encoding="utf-8")
dash = replace_once(
    dash,
    "        contentPadding = PaddingValues(\n            horizontal = 8.dp,\n            vertical = 8.dp\n        ),\n",
    "        contentPadding = PaddingValues(\n            start = 8.dp,\n            top = 0.dp,\n            end = 8.dp,\n            bottom = 8.dp\n        ),\n",
    "Dashboard top padding",
)
dash = replace_once(
    dash,
    "                latestTrip = latestCompletedTrip,\n                vehicleSwitchEnabled = state.activeTrip == null,\n",
    "                latestTrip = latestCompletedTrip,\n                edgeToEdgeTop = true,\n                vehicleSwitchEnabled = state.activeTrip == null,\n",
    "Dashboard Hero edge-to-edge flag",
)
dash_path.write_text(dash, encoding="utf-8")


hero_path = Path("android/app/src/main/java/com/evchargebook/ui/dashboard/HeroVehicleCard.kt")
hero = hero_path.read_text(encoding="utf-8")
for old, new, label in [
    ("import androidx.compose.foundation.layout.Row\n", "import androidx.compose.foundation.layout.Row\nimport androidx.compose.foundation.layout.WindowInsets\n", "Hero WindowInsets import"),
    ("import androidx.compose.foundation.layout.aspectRatio\n", "import androidx.compose.foundation.layout.aspectRatio\nimport androidx.compose.foundation.layout.asPaddingValues\n", "Hero asPaddingValues import"),
    ("import androidx.compose.foundation.layout.height\n", "import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.matchParentSize\n", "Hero matchParentSize import"),
    ("import androidx.compose.foundation.layout.size\n", "import androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.statusBars\n", "Hero statusBars import"),
    ("import androidx.compose.ui.draw.clip\n", "import androidx.compose.ui.draw.clip\nimport androidx.compose.ui.draw.shadow\n", "Hero shadow import"),
]:
    hero = replace_once(hero, old, new, label)

new_hero_function = r'''@Composable
fun HeroVehicleCard(
    vehicle: VehicleEntity?,
    currentSoc: Int? = null,
    currentMileageKm: Double? = null,
    latestTrip: TripSessionEntity? = null,
    vehicles: List<VehicleEntity> = emptyList(),
    vehicleSwitchEnabled: Boolean = true,
    onSelectVehicle: (Long) -> Unit = {},
    artworkKey: String? = null,
    edgeToEdgeTop: Boolean = false
) {
    val cockpit = LocalCockpitColors.current
    val context = LocalContext.current
    val catalogId = vehicle?.catalogVehicleId?.trim()?.takeIf { it.isNotEmpty() }
    val localArtworkKey by remember(catalogId, context.applicationContext) {
        catalogId?.let {
            AppDatabase.getInstance(context.applicationContext)
                .vehicleCatalogDao()
                .observeHeroArtworkKey(it)
        } ?: flowOf(null)
    }.collectAsState(initial = null)
    val effectiveArtworkKey = artworkKey?.trim()?.takeIf { it.isNotEmpty() } ?: localArtworkKey
    val topSystemInset = if (edgeToEdgeTop) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    } else {
        0.dp
    }

    val selectableVehicles = if (vehicles.isNotEmpty()) vehicles else listOfNotNull(vehicle)
    val canSwitchVehicle = vehicleSwitchEnabled && selectableVehicles.size > 1
    var vehicleMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color(0xFF06100C)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.46f)
        ) {
            VehicleStage(vehicle, effectiveArtworkKey, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xA8020806),
                                Color(0x28020806),
                                Color.Transparent,
                                Color(0x26020806),
                                Color(0x9806100C)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = 16.dp,
                        top = topSystemInset + 14.dp,
                        end = 72.dp
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(EVDesignTokens.Energy.green, CircleShape))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "MY EV",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = cockpit.primaryText
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    vehicle?.let { "${it.brand}  ${it.model}" } ?: "EV Charge Book",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = cockpit.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (vehicle != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = topSystemInset + 12.dp, end = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0x7207110F))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            .clickable(enabled = canSwitchVehicle) {
                                vehicleMenuExpanded = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = if (vehicleSwitchEnabled) "切换车辆" else "行程进行中不可切换车辆",
                            tint = Color.White.copy(alpha = if (vehicleSwitchEnabled) 1f else 0.45f),
                            modifier = Modifier.size(23.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = vehicleMenuExpanded && canSwitchVehicle,
                        onDismissRequest = { vehicleMenuExpanded = false }
                    ) {
                        selectableVehicles.forEach { candidate ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            candidate.model,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (candidate.id == vehicle.id) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                        Text(
                                            candidate.brand,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    vehicleMenuExpanded = false
                                    onSelectVehicle(candidate.id)
                                }
                            )
                        }
                    }
                }

                HeroDynamicStatePanel(
                    currentSoc = currentSoc,
                    currentMileageKm = currentMileageKm,
                    latestTrip = latestTrip,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, bottom = 12.dp)
                )
            }
        }
    }
}
'''
hero, count = re.subn(
    r'@Composable\nfun HeroVehicleCard\([\s\S]*?\n}\n\n@Composable\nprivate fun VehicleStage',
    new_hero_function + '\n@Composable\nprivate fun VehicleStage',
    hero,
    count=1,
)
if count != 1:
    raise SystemExit(f"HeroVehicleCard function: expected one replacement, got {count}")

new_panel_function = r'''@Composable
private fun HeroDynamicStatePanel(
    currentSoc: Int?,
    currentMileageKm: Double?,
    latestTrip: TripSessionEntity?,
    modifier: Modifier = Modifier
) {
    val safeSoc = currentSoc?.coerceIn(0, 100)
    val targetProgress = safeSoc?.div(100f) ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 650),
        label = "dashboard_hero_soc"
    )
    val panelShape = MaterialTheme.shapes.large

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = panelShape,
                ambientColor = Color.Black.copy(alpha = 0.38f),
                spotColor = Color.Black.copy(alpha = 0.50f)
            )
            .clip(panelShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color(0xB013211C),
                        Color(0xD00A1512)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.10f),
                        EVDesignTokens.Energy.green.copy(alpha = 0.16f)
                    )
                ),
                shape = panelShape
            )
    ) {
        // The panel now sits on top of the artwork. The translucent smoke/highlight layers
        // intentionally preserve some of the underlying image so the surface reads as glass
        // instead of a second opaque card. Compose has no true arbitrary backdrop blur here,
        // so the frost is created with controlled translucency and edge highlights.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0x381D6D49),
                            Color.Transparent,
                            Color(0x24174634)
                        )
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroSocMetric(
                safeSoc = safeSoc,
                animatedProgress = animatedProgress,
                modifier = Modifier.weight(0.92f)
            )
            MetricDivider()
            HeroMetric(
                icon = Icons.Default.Speed,
                label = "当前里程",
                value = currentMileageKm?.let(::formatMileage) ?: "--",
                unit = if (currentMileageKm != null) "km" else null,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )
            MetricDivider()
            HeroRecentTripMetric(
                trip = latestTrip,
                modifier = Modifier
                    .weight(1.08f)
                    .padding(start = 12.dp)
            )
        }
    }
}
'''
hero, count = re.subn(
    r'@Composable\nprivate fun HeroDynamicStatePanel\([\s\S]*?\n}\n\n@Composable\nprivate fun HeroSocMetric',
    new_panel_function + '\n@Composable\nprivate fun HeroSocMetric',
    hero,
    count=1,
)
if count != 1:
    raise SystemExit(f"HeroDynamicStatePanel function: expected one replacement, got {count}")
hero_path.write_text(hero, encoding="utf-8")

print("Applied dashboard edge-to-edge + glass Hero patch")
