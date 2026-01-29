package com.prantiux.pixelgallery.ui.icons

/**
 * Centralized Material Symbols Rounded Unicode Mapping
 * 
 * All unicode values are verified from fonts.google.com/icons
 * These represent the Material Symbols Rounded font codepoints.
 * 
 * Usage:
 * ```kotlin
 * FontIcon(
 *     unicode = FontIcons.Close,
 *     contentDescription = "Close",
 *     size = 24.sp,
 *     tint = Color.White
 * )
 * ```
 */
object FontIcons {
    // Navigation & Actions
    const val Close = "\ue5cd"
    const val ArrowBack = "\ue5c4"
    const val Done = "\ue876"
    const val Check = "\ue5ca"
    const val Clear = "\ue14c"
    const val Search = "\ue8b6"
    const val Refresh = "\ue5d5"
    const val KeyboardArrowRight = "\ue315"
    const val KeyboardArrowDown = "\ue313"
    
    // Media Controls
    const val PlayArrow = "\ue037"
    const val Pause = "\ue034"  // filled pause
    const val Fullscreen = "\ue5d0"
    const val FullscreenExit = "\ue5d1"
    
    // Gallery & Media
    const val Image = "\ue3f4"
    const val VideoLibrary = "\ue04a"
    const val CameraAlt = "\ue3af"
    const val Screenshot = "\uf05e"
    const val Delete = "\ue872"
    const val DeleteSweep = "\ue16c"
    const val Star = "\ue838"
    const val Lock = "\ue897"
    
    // View & Layout
    const val GridView = "\ue9b0"
    const val ViewList = "\ue8ef"
    const val ZoomIn = "\ue8ff"
    const val SwipeDown = "\ueb53"
    
    // Time & Date
    const val Timer = "\ue425"
    const val DateRange = "\ue916"
    const val Today = "\ue8df"
    const val CalendarToday = "\ue935"
    const val CalendarMonth = "\uebcc"
    const val History = "\ue889"
    
    // Appearance & Theme
    const val Palette = "\ue40a"
    const val ColorLens = "\ue3ae"
    const val Brightness2 = "\ue1a9"
    const val Brightness4 = "\ue1aa"
    const val LightMode = "\ue518"
    const val DarkMode = "\ue51c"
    
    // Settings & Info
    const val Settings = "\ue8b8"
    const val Info = "\ue88e"
    const val Feedback = "\ue87f"
    const val PrivacyTip = "\uf0dc"
    
    // Storage & File
    const val Storage = "\ue1db"
    const val CleaningServices = "\uf0ff"
    const val FolderOff = "\ueb83"
    const val Sort = "\ue164"
    
    // Privacy & Security
    const val VisibilityOff = "\ue8f5"
    
    // Video & Audio
    const val VolumeOff = "\ue04f"
    const val VolumeUp = "\ue050"
    
    // Actions & Menu
    const val MoreVert = "\ue5d4"
    const val Menu = "\ue3c7"  // menu icon (hamburger)
    const val Share = "\ue80d"
    const val StarOutline = "\ue83a"
    const val Add = "\ue145"
    const val Home = "\ue410"  // photo icon
    const val Person = "\uefb2"  // photo_library icon
    const val Edit = "\ue3c9"  // edit icon
    const val Copy = "\ue173"  // content_copy icon
    const val Move = "\uf1ff"  // drive_file_move icon
    const val Folder = "\ue2c7"  // folder icon
    const val LocationOn = "\ue0c8"  // location_on icon (filled pin)
    const val PushPin = "\uf10d"  // push_pin icon
    const val Tab = "\ue8d8"  // tab icon
    
    // Empty States
    const val SearchOff = "\uea76"
}
