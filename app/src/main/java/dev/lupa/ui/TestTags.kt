package dev.lupa.ui

/** Теги элементов для тестов. Тесты ищут элементы по ним, а не по координатам. */
object TestTags {
    const val ZOOM_INDICATOR = "zoomIndicator"
    const val ZOOM_SLIDER = "zoomSlider"
    const val BTN_TORCH = "btnTorch"
    const val BTN_FREEZE = "btnFreeze"
    const val BTN_FILTER = "btnFilter"
    const val BTN_SNAPSHOT = "btnSnapshot"
    const val BTN_SETTINGS = "btnSettings"
    const val CONTROLS = "controls"
    const val PREVIEW = "preview"
    const val FROZEN_IMAGE = "frozenImage"
    const val FOCUS_RING = "focusRing"
    const val FILTER_NAME = "filterName"
    const val MESSAGE = "message"
    const val PERMISSION_SCREEN = "permissionScreen"
    const val BTN_OPEN_SETTINGS = "btnOpenSettings"
    const val BTN_ASK_AGAIN = "btnAskAgain"
    const val CAMERA_ERROR = "cameraError"
    const val BTN_RETRY = "btnRetry"
    const val SETTINGS_SCREEN = "settingsScreen"
    const val BTN_BACK = "btnBack"
    const val SETTING_TORCH_ON_LAUNCH = "settingTorchOnLaunch"
    const val SETTING_VOLUME_KEYS = "settingVolumeKeys"
    const val SETTING_LAUNCH_ZOOM = "settingLaunchZoom"
    const val SETTING_KEEP_SCREEN_ON = "settingKeepScreenOn"
    const val SETTING_HAPTICS = "settingHaptics"
    const val SETTING_BUTTON_SIZE = "settingButtonSize"
    const val OPTION_ZOOM_LAST = "optionZoomLast"
    const val OPTION_ZOOM_FIXED = "optionZoomFixed"
    const val OPTION_BUTTONS_NORMAL = "optionButtonsNormal"
    const val OPTION_BUTTONS_LARGE = "optionButtonsLarge"

    /** Тег варианта фиксированного зума, например `optionFixedZoom1.5`. */
    fun fixedZoomOption(zoom: Float): String = "optionFixedZoom$zoom"

    /** Тег иконки внутри кнопки с тегом [buttonTag]. */
    fun iconOf(buttonTag: String): String = buttonTag + "Icon"
}
