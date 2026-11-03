@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.tv.material3.ExperimentalTvMaterial3Api::class
)

package com.darknet.animetv.ui.components

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.darknet.animetv.R
import com.darknet.animetv.data.model.UserAccount
import com.darknet.animetv.data.model.UserProfile
import com.darknet.animetv.ui.theme.TvAccentPurple
import com.darknet.animetv.ui.theme.TvBrandAmber
import com.darknet.animetv.ui.theme.TvBrandCyan
import com.darknet.animetv.ui.theme.TvBrandPurple
import com.darknet.animetv.ui.theme.TvBrandPurpleDark
import com.darknet.animetv.ui.theme.TvCardBorder
import com.darknet.animetv.ui.theme.TvDarkBackground
import com.darknet.animetv.ui.theme.TvGoldStar
import com.darknet.animetv.ui.theme.TvPrimaryRed
import com.darknet.animetv.ui.theme.TvSuccessGreen
import com.darknet.animetv.ui.theme.TvSurfaceColor
import com.darknet.animetv.ui.theme.TvSurfaceFocusedColor

/**
 * Resolves an avatar or background image model to either a bundled local Android drawable resource
 * or a direct remote/content URI string.
 */
fun resolveImageModel(model: String): Any {
    if (model.isBlank()) return ""
    return when {
        model == "res:avatar_chopper" || model == "avatar_chopper" ||
                model.contains("100534") || model.contains("chopper", ignoreCase = true) -> R.drawable.avatar_chopper
        model == "res:avatar_michiru" || model == "avatar_michiru" ||
                model.contains("408985") || model.contains("michiru", ignoreCase = true) -> R.drawable.avatar_michiru
        model == "res:avatar_raphtalia" || model == "avatar_raphtalia" ||
                model.contains("374824") || model.contains("raphtalia", ignoreCase = true) -> R.drawable.avatar_raphtalia
        model == "res:avatar_pompoko" || model == "avatar_pompoko" ||
                model.contains("48981") || model.contains("pompoko", ignoreCase = true) -> R.drawable.avatar_pompoko
        model == "res:avatar_tom_nook" || model == "avatar_tom_nook" ||
                model.contains("tom_nook", ignoreCase = true) || model.contains("nook", ignoreCase = true) -> R.drawable.avatar_tom_nook
        model == "res:bg_your_name" || model == "bg_your_name" ||
                model.contains("534447677768") || model.contains("your_name", ignoreCase = true) -> R.drawable.bg_your_name
        model == "res:bg_spirited_away" || model == "bg_spirited_away" ||
                model.contains("509198397868") || model.contains("spirited_away", ignoreCase = true) -> R.drawable.bg_spirited_away
        model == "res:bg_cyberpunk" || model == "bg_cyberpunk" ||
                model.contains("542751371") || model.contains("cyberpunk", ignoreCase = true) -> R.drawable.bg_cyberpunk
        model == "res:bg_frieren" || model == "bg_frieren" ||
                model.contains("518709268805") || model.contains("frieren", ignoreCase = true) -> R.drawable.bg_frieren
        model == "res:bg_demon_slayer" || model == "bg_demon_slayer" ||
                model.contains("507525428034") || model.contains("demon_slayer", ignoreCase = true) -> R.drawable.bg_demon_slayer
        else -> model
    }
}

/**
 * Character Preset for Avatar Icons (Tanuki / Raccoon anime characters and Chopper)
 */
data class AvatarCharacterPreset(
    val id: String,
    val name: String,
    val character: String,
    val anime: String,
    val emoji: String,
    val imageUrl: String = "",
    val badgeColor: Long = 0xFF8B52C9L
)

/**
 * Anime Background Scene Preset
 */
data class AnimeBackgroundPreset(
    val id: String,
    val title: String,
    val anime: String,
    val imageUrl: String,
    val gradientStart: Long = 0xFF2E1065L,
    val gradientEnd: Long = 0xFF0C0D14L
)

data class ProfileThemePreset(
    val id: String,
    val name: String,
    val gradientStart: Long,
    val gradientEnd: Long,
    val accentColor: Long
)

object ProfileCustomizationPresets {
    // Tanuki and Raccoon based anime characters including Tony Tony Chopper and Tom Nook
    val TANUKI_RACCOON_CHARACTERS = listOf(
        AvatarCharacterPreset(
            id = "chopper",
            name = "Tony Tony Chopper",
            character = "Chopper",
            anime = "One Piece",
            emoji = "🦌",
            imageUrl = "res:avatar_chopper",
            badgeColor = 0xFFE11D48L
        ),
        AvatarCharacterPreset(
            id = "michiru",
            name = "Michiru Kagemori",
            character = "Michiru (Tanuki)",
            anime = "BNA: Brand New Animal",
            emoji = "🦝",
            imageUrl = "res:avatar_michiru",
            badgeColor = 0xFF06B6D4L
        ),
        AvatarCharacterPreset(
            id = "raphtalia",
            name = "Raphtalia",
            character = "Raphtalia (Tanuki)",
            anime = "The Rising of the Shield Hero",
            emoji = "🦊",
            imageUrl = "res:avatar_raphtalia",
            badgeColor = 0xFFD97706L
        ),
        AvatarCharacterPreset(
            id = "pompoko",
            name = "Pompoko Tanuki",
            character = "Pompoko (Ghibli)",
            anime = "Pom Poko",
            emoji = "🍃",
            imageUrl = "res:avatar_pompoko",
            badgeColor = 0xFF10B981L
        ),
        AvatarCharacterPreset(
            id = "tom_nook",
            name = "Tom Nook",
            character = "Tom Nook (Tanuki)",
            anime = "Animal Crossing",
            emoji = "🦝",
            imageUrl = "res:avatar_tom_nook",
            badgeColor = 0xFFB45309L
        )
    )

    // Popular Anime Background Scenes
    val ANIME_BACKGROUND_SCENES = listOf(
        AnimeBackgroundPreset(
            id = "your_name_twilight",
            title = "Twilight Comet",
            anime = "Your Name (Kimi no Na wa)",
            imageUrl = "res:bg_your_name",
            gradientStart = 0xFF1E1B4BL,
            gradientEnd = 0xFF0C0D14L
        ),
        AnimeBackgroundPreset(
            id = "spirited_away_bridge",
            title = "Bathhouse Lantern Bridge",
            anime = "Spirited Away (Ghibli)",
            imageUrl = "res:bg_spirited_away",
            gradientStart = 0xFF7C2D12L,
            gradientEnd = 0xFF0C0D14L
        ),
        AnimeBackgroundPreset(
            id = "cyberpunk_skyline",
            title = "Night City Neon",
            anime = "Cyberpunk: Edgerunners",
            imageUrl = "res:bg_cyberpunk",
            gradientStart = 0xFF083344L,
            gradientEnd = 0xFF0C0D14L
        ),
        AnimeBackgroundPreset(
            id = "frieren_meadow",
            title = "Blue Moonflower Hill",
            anime = "Frieren: Beyond Journey's End",
            imageUrl = "res:bg_frieren",
            gradientStart = 0xFF064E3BL,
            gradientEnd = 0xFF0C0D14L
        ),
        AnimeBackgroundPreset(
            id = "demon_slayer_wisteria",
            title = "Wisteria Mountain",
            anime = "Demon Slayer (Kimetsu no Yaiba)",
            imageUrl = "res:bg_demon_slayer",
            gradientStart = 0xFF581C87L,
            gradientEnd = 0xFF0C0D14L
        )
    )

    val THEME_PRESETS = listOf(
        ProfileThemePreset("cosmic_purple", "Cosmic Purple", 0xFF3B0764L, 0xFF0C0D14L, 0xFF8B52C9L),
        ProfileThemePreset("neon_cyan", "Neon Cyberpunk", 0xFF083344L, 0xFF0C0D14L, 0xFF00E5FFL),
        ProfileThemePreset("tanuki_amber", "Tanuki Amber", 0xFF7C2D12L, 0xFF0C0D14L, 0xFFFF8A3DL),
        ProfileThemePreset("sakura_bloom", "Sakura Bloom", 0xFF831843L, 0xFF0C0D14L, 0xFFF472B6L),
        ProfileThemePreset("emerald_shinobi", "Emerald Forest", 0xFF064E3BL, 0xFF0C0D14L, 0xFF10B981L),
        ProfileThemePreset("midnight_obsidian", "Midnight Obsidian", 0xFF1E1B4BL, 0xFF0A0B10L, 0xFF6366F1L)
    )

    val NAME_PRESETS = listOf(
        "Chopper Senpai",
        "Tanuki Otaku",
        "Michiru Fan",
        "Raphtalia Shield",
        "Anime Night",
        "Kids Corner",
        "Cozy Watching"
    )

    val SAMPLE_UPLOAD_URLS_ICON = listOf(
        "https://cdn.myanimelist.net/images/characters/3/100534.jpg" to "Tony Tony Chopper",
        "https://cdn.myanimelist.net/images/characters/16/408985.jpg" to "Michiru (BNA Tanuki)",
        "https://cdn.myanimelist.net/images/characters/14/374824.jpg" to "Raphtalia (Tanuki)"
    )

    val SAMPLE_UPLOAD_URLS_BG = listOf(
        "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800&q=80" to "Your Name Comet",
        "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=800&q=80" to "Night City Cyberpunk",
        "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800&q=80" to "Spirited Away Lanterns"
    )
}

/**
 * Android TV Profile Switcher modal supporting custom avatar icons, popular anime background scenes,
 * local file upload / URL, profile customization, and TanukiTV Account login.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvProfileSwitcherModal(
    profiles: List<UserProfile>,
    activeProfile: UserProfile,
    userAccount: UserAccount? = null,
    onSelectProfile: (UserProfile) -> Unit,
    onSaveProfile: (UserProfile) -> Unit = {},
    onDeleteProfile: (String) -> Unit = {},
    onSaveAccount: (UserAccount) -> Unit = {},
    onLogoutAccount: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onDismiss() }

    val firstFocus = remember { FocusRequester() }
    var targetProfileNeedingPin by remember { mutableStateOf<UserProfile?>(null) }
    var editingProfile by remember { mutableStateOf<UserProfile?>(null) }
    var showingAccountModal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            firstFocus.requestFocus()
        } catch (_: Exception) {}
    }

    if (editingProfile != null) {
        TvProfileEditDialog(
            profile = editingProfile!!,
            onSave = { updated ->
                onSaveProfile(updated)
                editingProfile = null
            },
            onDelete = { idToDelete ->
                onDeleteProfile(idToDelete)
                editingProfile = null
            },
            onDismiss = { editingProfile = null },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    if (showingAccountModal) {
        TvAccountLoginDialog(
            currentAccount = userAccount,
            onLogin = { account ->
                onSaveAccount(account)
                showingAccountModal = false
            },
            onLogout = {
                onLogoutAccount()
                showingAccountModal = false
            },
            onDismiss = { showingAccountModal = false },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    if (targetProfileNeedingPin != null) {
        TvPinVerifyDialog(
            profile = targetProfileNeedingPin!!,
            onSuccess = {
                val p = targetProfileNeedingPin!!
                targetProfileNeedingPin = null
                onSelectProfile(p)
            },
            onDismiss = {
                targetProfileNeedingPin = null
            },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .focusProperties { exit = { FocusRequester.Cancel } },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(820.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(TvDarkBackground.copy(alpha = 0.98f))
                .border(2.dp, TvBrandCyan, RoundedCornerShape(24.dp))
                .padding(28.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                // Header with Account Pill & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "👤 Switch TV Profile",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Account Status Pill
                        Button(
                            onClick = { showingAccountModal = true },
                            colors = ButtonDefaults.colors(
                                containerColor = if (userAccount != null) TvBrandPurple.copy(alpha = 0.35f) else TvSurfaceFocusedColor,
                                focusedContainerColor = TvBrandPurple
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                            modifier = Modifier.tvFocusableItem(
                                onClick = { showingAccountModal = true },
                                focusedScale = 1.05f,
                                borderWidth = 2.dp,
                                focusedBorderColor = TvBrandCyan
                            )
                        ) {
                            Text(
                                text = if (userAccount != null) "👑 ${userAccount.username}" else "🔑 Sign In",
                                color = if (userAccount != null) TvBrandCyan else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = TvSurfaceColor,
                            focusedContainerColor = TvBrandPurple
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                        modifier = Modifier.tvFocusableItem(
                            onClick = onDismiss,
                            focusedScale = 1.08f,
                            borderWidth = 2.dp,
                            focusedBorderColor = TvBrandCyan
                        )
                    ) {
                        Text("✖ Close", color = Color.White, fontSize = 12.sp)
                    }
                }

                Text(
                    text = "Select a profile to switch watch histories, favorites, and parental filters",
                    color = Color(0xFFAAAAAA),
                    fontSize = 13.sp
                )

                // Profiles Horizontal Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    profiles.forEachIndexed { idx, profile ->
                        val isActive = profile.id == activeProfile.id
                        val cardGradient = Brush.verticalGradient(
                            colors = listOf(
                                Color(profile.bgGradientStart),
                                Color(profile.bgGradientEnd)
                            )
                        )

                        Box(
                            modifier = Modifier
                                .width(180.dp)
                                .height(220.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(cardGradient)
                                .border(
                                    width = if (isActive) 2.5.dp else 1.dp,
                                    color = if (isActive) TvBrandCyan else TvCardBorder,
                                    shape = RoundedCornerShape(18.dp)
                                )
                        ) {
                            // Background Anime Scene (if present)
                            if (profile.bgImageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = resolveImageModel(profile.bgImageUrl),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(18.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                // Gradient Scrim Overlay for contrast
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color(0x990C0D14),
                                                    Color(0xF50C0D14)
                                                )
                                            )
                                        )
                                    )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                // Profile Selection Button
                                Button(
                                    onClick = {
                                        if (profile.isPinLocked && profile.pinHash.isNotEmpty()) {
                                            targetProfileNeedingPin = profile
                                        } else {
                                            onSelectProfile(profile)
                                        }
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color.Transparent,
                                        focusedContainerColor = TvBrandPurple.copy(alpha = 0.45f)
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(14.dp)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (idx == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                        .tvFocusableItem(
                                            onClick = {
                                                if (profile.isPinLocked && profile.pinHash.isNotEmpty()) {
                                                    targetProfileNeedingPin = profile
                                                } else {
                                                    onSelectProfile(profile)
                                                }
                                            },
                                            focusedScale = 1.05f,
                                            borderWidth = 2.5.dp,
                                            focusedBorderColor = TvBrandCyan
                                        )
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        // Avatar: Check custom image/url vs character emoji
                                        if (profile.avatarUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = resolveImageModel(profile.avatarUrl),
                                                contentDescription = profile.name,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(profile.avatarColor))
                                                    .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = profile.avatarIcon.ifBlank { if (profile.isKidsMode) "👶" else "🦝" },
                                                    fontSize = 28.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = profile.name,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )

                                        if (isActive) {
                                            Text(
                                                text = "● Active",
                                                color = TvBrandCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else if (profile.isKidsMode) {
                                            Text(
                                                text = "Kids Safe",
                                                color = TvSuccessGreen,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else if (profile.isPinLocked) {
                                            Text(
                                                text = "🔒 PIN",
                                                color = TvGoldStar,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else {
                                            Text(
                                                text = profile.backgroundTheme,
                                                color = Color(0xFFAAAAAA),
                                                fontSize = 10.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                // Customize / Edit Button
                                Button(
                                    onClick = { editingProfile = profile },
                                    colors = ButtonDefaults.colors(
                                        containerColor = TvSurfaceColor.copy(alpha = 0.7f),
                                        focusedContainerColor = TvBrandPurpleDark
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tvFocusableItem(
                                            onClick = { editingProfile = profile },
                                            focusedScale = 1.05f,
                                            borderWidth = 2.dp,
                                            focusedBorderColor = TvBrandAmber
                                        )
                                ) {
                                    Text(
                                        text = "✏️ Customize",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Add Profile Card Button
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(220.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(TvSurfaceColor.copy(alpha = 0.5f))
                            .border(1.dp, TvCardBorder, RoundedCornerShape(18.dp))
                            .padding(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val newId = "profile_" + System.currentTimeMillis()
                                val newProfile = UserProfile(
                                    id = newId,
                                    name = "Chopper Fan",
                                    avatarIcon = "🦌",
                                    avatarUrl = "res:avatar_chopper",
                                    avatarColor = 0xFFE11D48L,
                                    backgroundTheme = "Twilight Comet",
                                    bgImageUrl = "res:bg_your_name",
                                    bgGradientStart = 0xFF1E1B4BL,
                                    bgGradientEnd = 0xFF0C0D14L
                                )
                                editingProfile = newProfile
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = TvBrandPurple.copy(alpha = 0.4f)
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(14.dp)),
                            modifier = Modifier
                                .fillMaxSize()
                                .tvFocusableItem(
                                    onClick = {
                                        val newId = "profile_" + System.currentTimeMillis()
                                        val newProfile = UserProfile(
                                            id = newId,
                                            name = "Chopper Fan",
                                            avatarIcon = "🦌",
                                            avatarUrl = "res:avatar_chopper",
                                            avatarColor = 0xFFE11D48L,
                                            backgroundTheme = "Twilight Comet",
                                            bgImageUrl = "res:bg_your_name",
                                            bgGradientStart = 0xFF1E1B4BL,
                                            bgGradientEnd = 0xFF0C0D14L
                                        )
                                        editingProfile = newProfile
                                    },
                                    focusedScale = 1.05f,
                                    borderWidth = 2.5.dp,
                                    focusedBorderColor = TvBrandCyan
                                )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(TvBrandCyan.copy(alpha = 0.15f))
                                        .border(2.dp, TvBrandCyan, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("➕", fontSize = 22.sp)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Add Profile",
                                    color = TvBrandCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Bottom Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (userAccount != null) "☁️ Sync: Enabled (Cloud Backup)" else "💡 Tip: Log in to sync across devices",
                        color = Color(0xFF888888),
                        fontSize = 12.sp
                    )

                    Button(
                        onClick = { showingAccountModal = true },
                        colors = ButtonDefaults.colors(
                            containerColor = TvSurfaceColor,
                            focusedContainerColor = TvBrandPurple
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                        modifier = Modifier.tvFocusableItem(
                            onClick = { showingAccountModal = true },
                            focusedScale = 1.05f,
                            borderWidth = 2.dp,
                            focusedBorderColor = TvBrandCyan
                        )
                    ) {
                        Text(
                            text = if (userAccount != null) "⚙️ Account Settings" else "🔑 Account Login",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive Profile Customization Dialog allowing changing Name,
 * Tanuki/Raccoon/Chopper Character Icons, Popular Anime Background Scenes, and Local File/URL Upload.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvProfileEditDialog(
    profile: UserProfile,
    onSave: (UserProfile) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onDismiss() }

    var nameState by remember { mutableStateOf(profile.name) }
    var iconState by remember { mutableStateOf(profile.avatarIcon.ifBlank { "🦝" }) }
    var avatarUrlState by remember { mutableStateOf(profile.avatarUrl) }
    var colorState by remember { mutableStateOf(profile.avatarColor) }
    var themeState by remember { mutableStateOf(profile.backgroundTheme) }
    var bgImageUrlState by remember { mutableStateOf(profile.bgImageUrl) }
    var gradStartState by remember { mutableStateOf(profile.bgGradientStart) }
    var gradEndState by remember { mutableStateOf(profile.bgGradientEnd) }
    var isKidsState by remember { mutableStateOf(profile.isKidsMode) }

    var showingUrlDialogForIcon by remember { mutableStateOf(false) }
    var showingUrlDialogForBg by remember { mutableStateOf(false) }

    val initialFocus = remember { FocusRequester() }

    // Android Storage File Picker for custom Icon
    val iconPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            avatarUrlState = uri.toString()
        }
    }

    // Android Storage File Picker for custom Background
    val bgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            bgImageUrlState = uri.toString()
            themeState = "Custom Upload"
        }
    }

    LaunchedEffect(Unit) {
        try {
            initialFocus.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .focusProperties { exit = { FocusRequester.Cancel } },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(820.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(TvDarkBackground.copy(alpha = 0.98f))
                .border(2.dp, TvBrandAmber, RoundedCornerShape(24.dp))
                .padding(26.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎨 Customize Profile",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.colors(
                        containerColor = TvSurfaceColor,
                        focusedContainerColor = TvBrandPurple
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.tvFocusableItem(
                        onClick = onDismiss,
                        focusedScale = 1.05f,
                        borderWidth = 2.dp,
                        focusedBorderColor = TvBrandCyan
                    )
                ) {
                    Text("Cancel", color = Color.White, fontSize = 12.sp)
                }
            }

            // Live Preview Card
            val previewGradient = Brush.verticalGradient(
                colors = listOf(Color(gradStartState), Color(gradEndState))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(previewGradient)
                    .border(2.dp, Color(colorState), RoundedCornerShape(16.dp))
            ) {
                // Background Image Preview (if set)
                if (bgImageUrlState.isNotBlank()) {
                    AsyncImage(
                        model = resolveImageModel(bgImageUrlState),
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x990C0D14), Color(0xF00C0D14))
                                )
                            )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .matchParentSize()
                        .padding(horizontal = 20.dp)
                ) {
                    if (avatarUrlState.isNotBlank()) {
                        AsyncImage(
                            model = resolveImageModel(avatarUrlState),
                            contentDescription = nameState,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(2.5.dp, Color.White, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(colorState))
                                .border(2.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = iconState, fontSize = 32.sp)
                        }
                    }

                    Column {
                        Text(
                            text = nameState.ifBlank { "Profile Name" },
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Scene: $themeState ${if (isKidsState) "• 👶 Kids Safe" else ""}",
                            color = Color(0xFFDDDDDD),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 1. Profile Name (Custom Input + Quick Suggestions)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "1. Profile Name",
                    color = TvBrandAmber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                var isTextFieldFocused by remember { mutableStateOf(false) }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isTextFieldFocused) TvBrandPurple.copy(alpha = 0.25f) else TvSurfaceColor)
                            .border(
                                width = if (isTextFieldFocused) 2.5.dp else 1.dp,
                                color = if (isTextFieldFocused) TvBrandCyan else TvCardBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = nameState,
                            onValueChange = { if (it.length <= 25) nameState = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            cursorBrush = SolidColor(TvBrandCyan),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(Modifier.focusRequester(initialFocus))
                                .onFocusChanged { isTextFieldFocused = it.isFocused },
                            decorationBox = { innerTextField ->
                                if (nameState.isEmpty()) {
                                    Text(
                                        text = "Enter custom profile name...",
                                        color = Color(0xFF777777),
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }

                    if (nameState.isNotEmpty()) {
                        Button(
                            onClick = { nameState = "" },
                            colors = ButtonDefaults.colors(
                                containerColor = TvSurfaceColor,
                                focusedContainerColor = Color(0xFFFF5252)
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                            modifier = Modifier
                                .height(46.dp)
                                .tvFocusableItem(
                                    onClick = { nameState = "" },
                                    focusedScale = 1.05f,
                                    borderWidth = 2.dp,
                                    focusedBorderColor = TvBrandCyan
                                )
                        ) {
                            Text("✖ Clear", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                // Quick Suggestions Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileCustomizationPresets.NAME_PRESETS.forEach { presetName ->
                        val isSelected = nameState == presetName
                        Button(
                            onClick = { nameState = presetName },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) TvBrandPurple.copy(alpha = 0.5f) else TvSurfaceColor.copy(alpha = 0.7f),
                                focusedContainerColor = TvBrandPurpleDark
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                            modifier = Modifier
                                .tvFocusableItem(
                                    onClick = { nameState = presetName },
                                    focusedScale = 1.06f,
                                    borderWidth = 1.5.dp,
                                    focusedBorderColor = TvBrandCyan
                                )
                        ) {
                            Text(
                                text = presetName,
                                color = if (isSelected) TvBrandCyan else Color(0xFFCCCCCC),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // 2. Choose Avatar Icon (Tanuki & Raccoon Anime Characters including Tony Tony Chopper)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. Avatar",
                        color = TvBrandAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Upload / Custom URL Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { iconPickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.colors(
                                containerColor = TvSurfaceColor,
                                focusedContainerColor = TvBrandPurple
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                            modifier = Modifier.tvFocusableItem(
                                onClick = { iconPickerLauncher.launch("image/*") },
                                focusedScale = 1.05f,
                                borderWidth = 1.5.dp,
                                focusedBorderColor = TvBrandCyan
                            )
                        ) {
                            Text("📁 Upload Icon", color = Color.White, fontSize = 10.sp)
                        }

                        Button(
                            onClick = { showingUrlDialogForIcon = true },
                            colors = ButtonDefaults.colors(
                                containerColor = TvSurfaceColor,
                                focusedContainerColor = TvBrandPurple
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                            modifier = Modifier.tvFocusableItem(
                                onClick = { showingUrlDialogForIcon = true },
                                focusedScale = 1.05f,
                                borderWidth = 1.5.dp,
                                focusedBorderColor = TvBrandCyan
                            )
                        ) {
                            Text("🌐 Icon URL", color = Color.White, fontSize = 10.sp)
                        }

                        if (avatarUrlState.isNotBlank()) {
                            Button(
                                onClick = { avatarUrlState = "" },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0x33FF5252),
                                    focusedContainerColor = Color(0xFFFF5252)
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                modifier = Modifier.tvFocusableItem(
                                    onClick = { avatarUrlState = "" },
                                    focusedScale = 1.05f,
                                    borderWidth = 1.5.dp,
                                    focusedBorderColor = Color.White
                                )
                            ) {
                                Text("✖ Reset", color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileCustomizationPresets.TANUKI_RACCOON_CHARACTERS.forEach { charPreset ->
                        val isSelected = (avatarUrlState.isBlank() && iconState == charPreset.emoji) ||
                                (avatarUrlState == charPreset.imageUrl && charPreset.imageUrl.isNotBlank())

                        Button(
                            onClick = {
                                iconState = charPreset.emoji
                                avatarUrlState = charPreset.imageUrl
                                colorState = charPreset.badgeColor
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) Color(charPreset.badgeColor).copy(alpha = 0.5f) else TvSurfaceColor,
                                focusedContainerColor = TvBrandPurple
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                            modifier = Modifier
                                .width(115.dp)
                                .height(76.dp)
                                .tvFocusableItem(
                                    onClick = {
                                        iconState = charPreset.emoji
                                        avatarUrlState = charPreset.imageUrl
                                        colorState = charPreset.badgeColor
                                    },
                                    focusedScale = 1.08f,
                                    borderWidth = 2.dp,
                                    focusedBorderColor = TvBrandCyan
                                )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (charPreset.imageUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = resolveImageModel(charPreset.imageUrl),
                                        contentDescription = charPreset.name,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(text = charPreset.emoji, fontSize = 24.sp)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = charPreset.character,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 3. Choose Profile Background (Popular Anime Background Scenes & Upload)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "3. Anime Background Scene",
                        color = TvBrandAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Upload / Custom URL for Background
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { bgPickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.colors(
                                containerColor = TvSurfaceColor,
                                focusedContainerColor = TvBrandPurple
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                            modifier = Modifier.tvFocusableItem(
                                onClick = { bgPickerLauncher.launch("image/*") },
                                focusedScale = 1.05f,
                                borderWidth = 1.5.dp,
                                focusedBorderColor = TvBrandCyan
                            )
                        ) {
                            Text("📁 Upload BG", color = Color.White, fontSize = 10.sp)
                        }

                        Button(
                            onClick = { showingUrlDialogForBg = true },
                            colors = ButtonDefaults.colors(
                                containerColor = TvSurfaceColor,
                                focusedContainerColor = TvBrandPurple
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                            modifier = Modifier.tvFocusableItem(
                                onClick = { showingUrlDialogForBg = true },
                                focusedScale = 1.05f,
                                borderWidth = 1.5.dp,
                                focusedBorderColor = TvBrandCyan
                            )
                        ) {
                            Text("🌐 BG URL", color = Color.White, fontSize = 10.sp)
                        }

                        if (bgImageUrlState.isNotBlank()) {
                            Button(
                                onClick = {
                                    bgImageUrlState = ""
                                    themeState = "Cosmic Purple"
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0x33FF5252),
                                    focusedContainerColor = Color(0xFFFF5252)
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                modifier = Modifier.tvFocusableItem(
                                    onClick = {
                                        bgImageUrlState = ""
                                        themeState = "Cosmic Purple"
                                    },
                                    focusedScale = 1.05f,
                                    borderWidth = 1.5.dp,
                                    focusedBorderColor = Color.White
                                )
                            ) {
                                Text("✖ Reset", color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }

                // Anime Scene Cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileCustomizationPresets.ANIME_BACKGROUND_SCENES.forEach { scenePreset ->
                        val isSelected = bgImageUrlState == scenePreset.imageUrl

                        Button(
                            onClick = {
                                themeState = scenePreset.title
                                bgImageUrlState = scenePreset.imageUrl
                                gradStartState = scenePreset.gradientStart
                                gradEndState = scenePreset.gradientEnd
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = TvBrandPurple.copy(alpha = 0.5f)
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                            modifier = Modifier
                                .width(140.dp)
                                .height(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) TvBrandCyan else TvCardBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .tvFocusableItem(
                                    onClick = {
                                        themeState = scenePreset.title
                                        bgImageUrlState = scenePreset.imageUrl
                                        gradStartState = scenePreset.gradientStart
                                        gradEndState = scenePreset.gradientEnd
                                    },
                                    focusedScale = 1.08f,
                                    borderWidth = 2.5.dp,
                                    focusedBorderColor = TvBrandCyan
                                )
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = resolveImageModel(scenePreset.imageUrl),
                                    contentDescription = scenePreset.title,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color(0x990C0D14))
                                )
                                Column(
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .align(Alignment.BottomStart)
                                ) {
                                    Text(
                                        text = scenePreset.title,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = scenePreset.anime,
                                        color = TvBrandAmber,
                                        fontSize = 8.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Parental Controls (Kids Mode)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "👶 Kids Safe Mode",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Filter mature content, violence, and adult genres",
                        color = Color(0xFFAAAAAA),
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = { isKidsState = !isKidsState },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isKidsState) TvSuccessGreen else TvSurfaceColor,
                        focusedContainerColor = TvBrandPurple
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.tvFocusableItem(
                        onClick = { isKidsState = !isKidsState },
                        focusedScale = 1.08f,
                        borderWidth = 2.dp,
                        focusedBorderColor = TvBrandCyan
                    )
                ) {
                    Text(
                        text = if (isKidsState) "✓ Enabled" else "Disabled",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom Actions: Save, Delete, Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val updated = profile.copy(
                            name = nameState.ifBlank { "Profile" },
                            avatarIcon = iconState,
                            avatarUrl = avatarUrlState,
                            avatarColor = colorState,
                            backgroundTheme = themeState,
                            bgImageUrl = bgImageUrlState,
                            bgGradientStart = gradStartState,
                            bgGradientEnd = gradEndState,
                            isKidsMode = isKidsState
                        )
                        onSave(updated)
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = TvBrandPurple,
                        focusedContainerColor = TvBrandCyan
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusableItem(
                            onClick = {
                                val updated = profile.copy(
                                    name = nameState.ifBlank { "Profile" },
                                    avatarIcon = iconState,
                                    avatarUrl = avatarUrlState,
                                    avatarColor = colorState,
                                    backgroundTheme = themeState,
                                    bgImageUrl = bgImageUrlState,
                                    bgGradientStart = gradStartState,
                                    bgGradientEnd = gradEndState,
                                    isKidsMode = isKidsState
                                )
                                onSave(updated)
                            },
                            focusedScale = 1.05f,
                            borderWidth = 2.5.dp,
                            focusedBorderColor = Color.White
                        )
                ) {
                    Text("💾 Save Profile", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                if (profile.id != "profile_main") {
                    Button(
                        onClick = { onDelete(profile.id) },
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0x33FF5252),
                            focusedContainerColor = Color(0xFFFF5252)
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                        modifier = Modifier.tvFocusableItem(
                            onClick = { onDelete(profile.id) },
                            focusedScale = 1.05f,
                            borderWidth = 2.dp,
                            focusedBorderColor = Color.White
                        )
                    ) {
                        Text("🗑 Delete", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

    // Modal to pick a custom Web Image URL for Icon
    if (showingUrlDialogForIcon) {
        TvQuickUrlPickerModal(
            title = "🌐 Custom Icon URL",
            subtitle = "Choose a preset character or enter a direct image URL",
            samples = ProfileCustomizationPresets.SAMPLE_UPLOAD_URLS_ICON,
            onSelectUrl = { url ->
                avatarUrlState = url
                showingUrlDialogForIcon = false
            },
            onDismiss = { showingUrlDialogForIcon = false },
            modifier = Modifier.fillMaxSize()
        )
    }

    // Modal to pick a custom Web Image URL for Background
    if (showingUrlDialogForBg) {
        TvQuickUrlPickerModal(
            title = "🌐 Custom Anime Scene Background URL",
            subtitle = "Choose a sample scene or enter a direct image URL",
            samples = ProfileCustomizationPresets.SAMPLE_UPLOAD_URLS_BG,
            onSelectUrl = { url ->
                bgImageUrlState = url
                themeState = "Custom Anime Scene"
                showingUrlDialogForBg = false
            },
            onDismiss = { showingUrlDialogForBg = false },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Quick URL Picker dialog allowing selecting from sample links or custom inputs
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvQuickUrlPickerModal(
    title: String,
    subtitle: String,
    samples: List<Pair<String, String>>,
    onSelectUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onDismiss() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .focusProperties { exit = { FocusRequester.Cancel } },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(TvDarkBackground.copy(alpha = 0.98f))
                .border(2.dp, TvBrandCyan, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = TvSurfaceColor,
                            focusedContainerColor = TvBrandPurple
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = Modifier.tvFocusableItem(onClick = onDismiss, focusedScale = 1.05f)
                    ) {
                        Text("Close", color = Color.White, fontSize = 12.sp)
                    }
                }

                Text(text = subtitle, color = Color(0xFFAAAAAA), fontSize = 12.sp)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    samples.forEach { (url, label) ->
                        Button(
                            onClick = { onSelectUrl(url) },
                            colors = ButtonDefaults.colors(
                                containerColor = TvSurfaceColor,
                                focusedContainerColor = TvBrandPurpleDark
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusableItem(
                                    onClick = { onSelectUrl(url) },
                                    focusedScale = 1.04f,
                                    borderWidth = 2.dp,
                                    focusedBorderColor = TvBrandCyan
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Text("🔗", fontSize = 14.sp)
                                Column {
                                    Text(text = label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(text = url, color = Color(0xFF888888), fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Account login and cloud synchronization dialog for TanukiTV.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAccountLoginDialog(
    currentAccount: UserAccount?,
    onLogin: (UserAccount) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onDismiss() }

    val initialFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            initialFocus.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .focusProperties { exit = { FocusRequester.Cancel } },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(540.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(TvDarkBackground.copy(alpha = 0.98f))
                .border(2.dp, TvBrandPurple, RoundedCornerShape(24.dp))
                .padding(26.dp)
        ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🦝", fontSize = 24.sp)
                    Text(
                        text = "TanukiTV Account",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.colors(
                        containerColor = TvSurfaceColor,
                        focusedContainerColor = TvBrandPurple
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.tvFocusableItem(
                        onClick = onDismiss,
                        focusedScale = 1.05f,
                        borderWidth = 2.dp,
                        focusedBorderColor = TvBrandCyan
                    )
                ) {
                    Text("Close", color = Color.White, fontSize = 12.sp)
                }
            }

            if (currentAccount != null) {
                // Logged-in State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TvBrandPurpleDark.copy(alpha = 0.35f))
                        .border(1.5.dp, TvBrandCyan, RoundedCornerShape(16.dp))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "👤 ${currentAccount.username}",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "👑 ${currentAccount.membershipTier}",
                                color = TvBrandAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Email: ${currentAccount.email}",
                            color = Color(0xFFAAAAAA),
                            fontSize = 13.sp
                        )

                        Text(
                            text = "☁️ Cloud Watchlist & History: Synchronized",
                            color = TvSuccessGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0x33FF5252),
                        focusedContainerColor = Color(0xFFFF5252)
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(initialFocus)
                        .tvFocusableItem(
                            onClick = onLogout,
                            focusedScale = 1.05f,
                            borderWidth = 2.dp,
                            focusedBorderColor = Color.White
                        )
                ) {
                    Text("🚪 Sign Out of TanukiTV", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                // Not Logged In State
                Text(
                    text = "Sign in to synchronize your watchlist, favorites, and watch history across all your TV and mobile devices.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 13.sp
                )

                // Quick One-Click Demo Login
                Button(
                    onClick = {
                        val acc = UserAccount(
                            email = "chopper@tanukitv.com",
                            username = "ChopperVIP",
                            membershipTier = "TanukiTV VIP",
                            syncEnabled = true
                        )
                        onLogin(acc)
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = TvBrandPurple,
                        focusedContainerColor = TvBrandCyan
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(initialFocus)
                        .tvFocusableItem(
                            onClick = {
                                val acc = UserAccount(
                                    email = "chopper@tanukitv.com",
                                    username = "ChopperVIP",
                                    membershipTier = "TanukiTV VIP",
                                    syncEnabled = true
                                )
                                onLogin(acc)
                            },
                            focusedScale = 1.05f,
                            borderWidth = 2.5.dp,
                            focusedBorderColor = Color.White
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text("🦌", fontSize = 18.sp)
                        Column {
                            Text("Quick Login as ChopperVIP", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Instant VIP membership with full cloud sync", color = Color(0xFFDDDDDD), fontSize = 11.sp)
                        }
                    }
                }

                // Alternate Preset Account Login
                Button(
                    onClick = {
                        val acc = UserAccount(
                            email = "tanuki@tanukitv.com",
                            username = "TanukiMaster",
                            membershipTier = "Active Member",
                            syncEnabled = true
                        )
                        onLogin(acc)
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = TvSurfaceColor,
                        focusedContainerColor = TvBrandPurpleDark
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusableItem(
                            onClick = {
                                val acc = UserAccount(
                                    email = "tanuki@tanukitv.com",
                                    username = "TanukiMaster",
                                    membershipTier = "Active Member",
                                    syncEnabled = true
                                )
                                onLogin(acc)
                            },
                            focusedScale = 1.05f,
                            borderWidth = 2.dp,
                            focusedBorderColor = TvBrandCyan
                        )
                ) {
                    Text("🦝 Sign in as TanukiMaster (Standard Member)", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
}

/**
 * 4-digit PIN verification dialog for parental controls and adult profile protection.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPinVerifyDialog(
    profile: UserProfile,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onDismiss() }

    var enteredPin by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .focusProperties { exit = { FocusRequester.Cancel } },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(TvDarkBackground.copy(alpha = 0.98f))
                .border(2.dp, if (hasError) TvPrimaryRed else TvAccentPurple, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "🔒 Enter Parental PIN",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Profile '${profile.name}' is PIN protected",
                color = Color(0xFFAAAAAA),
                fontSize = 13.sp
            )

            // PIN Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                repeat(4) { idx ->
                    val isFilled = idx < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (isFilled) TvPrimaryRed else Color(0x33FFFFFF))
                    )
                }
            }

            if (hasError) {
                Text(
                    text = "Incorrect PIN. Try again.",
                    color = TvPrimaryRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Keypad
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "OK")
                ).forEachIndexed { rowIdx, row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEachIndexed { colIdx, digit ->
                            Button(
                                onClick = {
                                    when (digit) {
                                        "C" -> {
                                            enteredPin = ""
                                            hasError = false
                                        }
                                        "OK" -> {
                                            if (enteredPin == profile.pinHash || profile.pinHash.isEmpty()) {
                                                onSuccess()
                                            } else {
                                                hasError = true
                                                enteredPin = ""
                                            }
                                        }
                                        else -> {
                                            if (enteredPin.length < 4) {
                                                enteredPin += digit
                                                hasError = false
                                                if (enteredPin.length == 4) {
                                                    if (enteredPin == profile.pinHash || profile.pinHash.isEmpty()) {
                                                        onSuccess()
                                                    } else {
                                                        hasError = true
                                                        enteredPin = ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = TvSurfaceColor,
                                    focusedContainerColor = TvAccentPurple
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (rowIdx == 0 && colIdx == 0) Modifier.focusRequester(focusRequester) else Modifier)
                                    .tvFocusableItem(
                                        onClick = {
                                            when (digit) {
                                                "C" -> {
                                                    enteredPin = ""
                                                    hasError = false
                                                }
                                                "OK" -> {
                                                    if (enteredPin == profile.pinHash || profile.pinHash.isEmpty()) {
                                                        onSuccess()
                                                    } else {
                                                        hasError = true
                                                        enteredPin = ""
                                                    }
                                                }
                                                else -> {
                                                    if (enteredPin.length < 4) {
                                                        enteredPin += digit
                                                        hasError = false
                                                        if (enteredPin.length == 4) {
                                                            if (enteredPin == profile.pinHash || profile.pinHash.isEmpty()) {
                                                                onSuccess()
                                                            } else {
                                                                hasError = true
                                                                enteredPin = ""
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        focusedScale = 1.08f,
                                        borderWidth = 2.dp,
                                        focusedBorderColor = Color.White
                                    )
                            ) {
                                Text(
                                    text = digit,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = TvSurfaceColor
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusableItem(
                        onClick = onDismiss,
                        focusedScale = 1.05f,
                        borderWidth = 2.dp,
                        focusedBorderColor = Color.White
                    )
            ) {
                Text("Cancel", color = Color(0xFFAAAAAA), fontSize = 12.sp)
            }
        }
    }
}
}
