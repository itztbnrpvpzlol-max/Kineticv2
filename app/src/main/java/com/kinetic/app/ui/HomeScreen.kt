package com.kinetic.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kinetic.app.R
import com.kinetic.app.data.ProjectStore
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    onNewComposition: () -> Unit,
    onImportProject: () -> Unit,
    onImportPlugin: () -> Unit,
    onOpenSettings: () -> Unit,
    projects: List<ProjectStore.ProjectInfo>,
    onOpenProject: (ProjectStore.ProjectInfo) -> Unit,
    onDeleteProject: (ProjectStore.ProjectInfo) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(projects, query) {
        if (query.isBlank()) projects else projects.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize().background(KColors.Bg).statusBarsPadding().navigationBarsPadding()) {
        Header(onOpenSettings)
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(22.dp))
            NewCompositionCard(onNewComposition)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Import project", onImportProject, Modifier.weight(1f), IconKind.IMPORT)
                ActionButton("Import plugin", onImportPlugin, Modifier.weight(1f), IconKind.PLUG)
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your workspace", color = KColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("${projects.size} saved ${if (projects.size == 1) "project" else "projects"}", color = KColors.Muted, fontSize = 12.sp)
                }
                Text("RECENT", color = KColors.Dim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                singleLine = true,
                placeholder = { Text("Find a project", color = KColors.Dim, fontSize = 14.sp) },
                leadingIcon = { Text("⌕", color = KColors.Muted, fontSize = 23.sp) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = KColors.Text, unfocusedTextColor = KColors.Text,
                    focusedBorderColor = KColors.Accent, unfocusedBorderColor = KColors.Line,
                    cursorColor = KColors.Accent, focusedContainerColor = KColors.Surface,
                    unfocusedContainerColor = KColors.Surface
                )
            )
            Spacer(Modifier.height(12.dp))
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (projects.isEmpty()) "Your next idea starts here" else "No matching projects", color = KColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(if (projects.isEmpty()) "Create a composition or import an existing project." else "Try another project name.", color = KColors.Muted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { project ->
                        ProjectCard(project, onClick = { onOpenProject(project) }, onDelete = { onDeleteProject(project) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(painterResource(R.drawable.kinetic_icon), "Kinetic", Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("KINETIC", color = KColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("MOTION, IN EVERY FRAME.", color = KColors.Dim, fontSize = 9.sp, letterSpacing = 1.1.sp)
            }
            IconButton(onClick = onOpenSettings) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(KColors.Surface), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Settings, "Preferences", tint = KColors.Muted)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(KColors.Line))
    }
}

@Composable
private fun NewCompositionCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.fillMaxWidth().height(190.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF30265E), Color(0xFF1C1B35), Color(0xFF171722))))
            .border(1.dp, Color(0xFF51458A).copy(alpha = .55f), shape)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .09f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("NEW PROJECT", color = Color(0xFFC9BEFF), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("✦", color = Color(0xFFB8A8FF), fontSize = 22.sp)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Make something", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("move.", color = Color(0xFFB9A8FF), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("Start with a blank composition.", color = Color(0xFFB5B1C9), fontSize = 12.sp)
                }
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(KColors.Fab), contentAlignment = Alignment.Center) {
                    Text("+", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
                }
            }
        }
        CanvasDecoration()
    }
}

@Composable
private fun CanvasDecoration() {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val x = size.width * .68f
        val y = size.height * .48f
        drawCircle(Color(0xFF8B78FF).copy(alpha = .10f), size.minDimension * .35f, androidx.compose.ui.geometry.Offset(x, y))
        drawCircle(Color(0xFFB9A8FF).copy(alpha = .28f), 4.dp.toPx(), androidx.compose.ui.geometry.Offset(x + 54.dp.toPx(), y - 38.dp.toPx()))
        drawLine(Color(0xFFB9A8FF).copy(alpha = .18f), androidx.compose.ui.geometry.Offset(x - 34.dp.toPx(), y + 35.dp.toPx()), androidx.compose.ui.geometry.Offset(x + 48.dp.toPx(), y - 32.dp.toPx()), 1.dp.toPx())
    }
}

@Composable
private fun ProjectCard(project: ProjectStore.ProjectInfo, onClick: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(KColors.Surface)
            .border(1.dp, KColors.Line, shape).clickable(onClick = onClick)
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(Color(0xFF39316E), Color(0xFF211F39)))), contentAlignment = Alignment.Center) {
            Text("K", color = Color(0xFFD4CCFF), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(project.name, color = KColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${project.layerCount} layers  ·  ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(project.updatedAt))}", color = KColors.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("AUTOSAVED", color = Color(0xFF8C82C7), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Box(Modifier.size(42.dp).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
            Text("⋯", color = KColors.Muted, fontSize = 23.sp)
        }
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: IconKind) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.clip(shape).background(KColors.Surface).border(1.dp, KColors.Line, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        KIcon(icon, KColors.ClipText, Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = KColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
