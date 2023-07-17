package com.simplemobiletools.launcher.views

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.AttributeSet
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import com.simplemobiletools.commons.extensions.isPackageInstalled
import com.simplemobiletools.commons.extensions.performHapticFeedback
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.launcher.activities.MainActivity
import com.simplemobiletools.launcher.extensions.getDrawableForPackageName
import com.simplemobiletools.launcher.extensions.homeScreenGridItemsDB
import com.simplemobiletools.launcher.extensions.launchersDB
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_ICON
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_SHORTCUT
import com.simplemobiletools.launcher.models.AppLauncher
import com.simplemobiletools.launcher.models.HomeScreenGridItem

class HomeScreenDock(context: Context, attrs: AttributeSet, defStyle: Int) : BaseLauncherGrid(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    init {
        sideMargins.apply {
            top = sideMargins.left
        }

        fetchDockItems()
    }

    private fun getDefaultAppPackages(appLaunchers: List<AppLauncher>): List<HomeScreenGridItem> {
        val homeScreenGridItems = ArrayList<HomeScreenGridItem>()
        try {
            val defaultDialerPackage = (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
            appLaunchers.firstOrNull { it.packageName == defaultDialerPackage }?.apply {
                val dialerIcon =
                    HomeScreenGridItem(0, 0, 0, 0, 0, defaultDialerPackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null)
                homeScreenGridItems.add(dialerIcon)
            }
        } catch (e: Exception) {
        }

        try {
            val defaultSMSMessengerPackage = Telephony.Sms.getDefaultSmsPackage(context)
            appLaunchers.firstOrNull { it.packageName == defaultSMSMessengerPackage }?.apply {
                val SMSMessengerIcon =
                    HomeScreenGridItem(1, 1, 0, 1, 0, defaultSMSMessengerPackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null)
                homeScreenGridItems.add(SMSMessengerIcon)
            }
        } catch (e: Exception) {
        }

        try {
            val browserIntent = Intent("android.intent.action.VIEW", Uri.parse("http://"))
            val resolveInfo = context.packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val defaultBrowserPackage = resolveInfo!!.activityInfo.packageName
            appLaunchers.firstOrNull { it.packageName == defaultBrowserPackage }?.apply {
                val browserIcon =
                    HomeScreenGridItem(2, 2, 0, 2, 0, defaultBrowserPackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null)
                homeScreenGridItems.add(browserIcon)
            }
        } catch (e: Exception) {
        }

        try {
            val potentialStores = arrayListOf("com.android.vending", "org.fdroid.fdroid", "com.aurora.store")
            val storePackage = potentialStores.firstOrNull { context.isPackageInstalled(it) && appLaunchers.map { it.packageName }.contains(it) }
            if (storePackage != null) {
                appLaunchers.firstOrNull { it.packageName == storePackage }?.apply {
                    val storeIcon = HomeScreenGridItem(3, 3,0, 3, 0, storePackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null)
                    homeScreenGridItems.add(storeIcon)
                }
            }
        } catch (e: Exception) {
        }

        try {
            val cameraIntent = Intent("android.media.action.IMAGE_CAPTURE")
            val resolveInfo = context.packageManager.resolveActivity(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val defaultCameraPackage = resolveInfo!!.activityInfo.packageName
            appLaunchers.firstOrNull { it.packageName == defaultCameraPackage }?.apply {
                val cameraIcon =
                    HomeScreenGridItem(4, 4, 0, 4, 0, defaultCameraPackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null)
                homeScreenGridItems.add(cameraIcon)
            }
        } catch (e: Exception) {
        }

        return homeScreenGridItems
    }

    fun fetchDockItems() {
        ensureBackgroundThread {
            gridItems = ArrayList(getDefaultAppPackages(context.launchersDB.getAppLaunchers()))
            gridItems.forEach { item ->
                if (item.type == ITEM_TYPE_ICON) {
                    item.drawable = context.getDrawableForPackageName(item.packageName)
                } else if (item.type == ITEM_TYPE_SHORTCUT) {
                    if (item.icon != null) {
                        item.drawable = BitmapDrawable(item.icon)
                    } else {
                        ensureBackgroundThread {
                            context.homeScreenGridItemsDB.deleteById(item.id!!)
                        }
                    }
                }
            }

            redrawGrid()
        }
    }

    override fun removeItem(item: HomeScreenGridItem) {
        ensureBackgroundThread {
            removeItemFromDock(item)

            gridItems.removeIf { it.id == item.id }
            redrawGrid()
        }
    }

    private fun removeItemFromDock(item: HomeScreenGridItem) {
        ensureBackgroundThread {
            if (item.id != null) {
//                context.homeScreenGridItemsDB.deleteById(item.id!!)
            }
        }
    }

    override fun drawGridIcon(item: HomeScreenGridItem, canvas: Canvas, extraXMargin: Int, extraYMargin: Int) {
        val drawableX = cellXCoords[item.left] + iconMargin + extraXMargin + sideMargins.left
        val drawableY = cellYCoords[item.top] + iconMargin * 2 + extraYMargin + sideMargins.top
        item.drawable!!.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)

        item.drawable!!.draw(canvas)
    }

    override fun getIconCenter(cell: Pair<Int, Int>, extraXMargin: Int, extraYMargin: Int): Pair<Float, Float> {
        val x = cellXCoords[cell.first] + iconMargin + iconSize / 2f + extraXMargin + sideMargins.left
        val y = cellYCoords[cell.second] + iconMargin * 2 + iconSize / 2f + extraYMargin + sideMargins.top
        return Pair(x, y)
    }

    override fun addAppIconOrShortcut() {
        // TODO UPDATE
        val center = gridCenters.minBy {
            Math.abs(it.first - draggedItemCurrentCoords.first + sideMargins.left) + Math.abs(it.second - draggedItemCurrentCoords.second + sideMargins.top)
        }

        var redrawIcons = false
        val gridCells = getClosestGridCells(center)
        if (gridCells != null) {
            val xIndex = gridCells.first
            val yIndex = gridCells.second

            // check if the destination cell is empty
            var areAllCellsEmpty = true
            val wantedCell = Pair(xIndex, yIndex)
            gridItems.forEach { item ->
                for (xCell in item.left..item.right) {
                    for (yCell in item.top..item.bottom) {
                        val cell = Pair(xCell, yCell)
                        val isAnyCellOccupied = wantedCell == cell
                        if (isAnyCellOccupied) {
                            areAllCellsEmpty = false
                            return@forEach
                        }
                    }
                }
            }

            if (areAllCellsEmpty) {
                val draggedHomeGridItem = gridItems.firstOrNull { it.id == draggedItem?.id }

                // we are moving an existing home screen item from one place to another
                if (draggedHomeGridItem != null) {
                    draggedHomeGridItem.apply {
                        left = xIndex
                        top = yIndex
                        right = xIndex
                        bottom = yIndex

                        ensureBackgroundThread {
                            context.homeScreenGridItemsDB.updateItemPosition(left, top, right, bottom, id!!)
                        }
                    }
                    redrawIcons = true
                } else if (draggedItem != null) {
                    // we are dragging a new item at the home screen from the All Apps fragment
                    val newHomeScreenGridItem = HomeScreenGridItem(
                        null,
                        xIndex,
                        yIndex,
                        xIndex,
                        yIndex,
                        draggedItem!!.packageName,
                        draggedItem!!.activityName,
                        draggedItem!!.title,
                        draggedItem!!.type,
                        "",
                        -1,
                        "",
                        "",
                        draggedItem!!.icon,
                        draggedItem!!.drawable,
                        draggedItem!!.providerInfo,
                        draggedItem!!.activityInfo
                    )

                    if (newHomeScreenGridItem.type == ITEM_TYPE_ICON) {
                        ensureBackgroundThread {
                            storeAndShowGridItem(newHomeScreenGridItem)
                        }
                    } else if (newHomeScreenGridItem.type == ITEM_TYPE_SHORTCUT) {
                        (context as? MainActivity)?.handleShorcutCreation(newHomeScreenGridItem.activityInfo!!) { label, icon, intent ->
                            ensureBackgroundThread {
                                newHomeScreenGridItem.title = label
                                newHomeScreenGridItem.icon = icon
                                newHomeScreenGridItem.intent = intent
                                newHomeScreenGridItem.drawable = BitmapDrawable(icon)
                                storeAndShowGridItem(newHomeScreenGridItem)
                            }
                        }
                    }
                }
            } else {
                performHapticFeedback()
                redrawIcons = true
            }
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        if (redrawIcons) {
            redrawGrid()
        }
    }

    fun storeAndShowGridItem(item: HomeScreenGridItem) {
        val newId = context.homeScreenGridItemsDB.insert(item)
        item.id = newId
        gridItems.add(item)
        redrawGrid()
    }

    override fun getWidgetOccupiedRect(item: Pair<Int, Int>): Rect = throwForWidgetMethods()
    override fun bindWidget(item: HomeScreenGridItem, isInitialDrawAfterLaunch: Boolean) = throwForWidgetMethods()
    override fun addWidget() = throwForWidgetMethods()
    override fun calculateWidgetY(top: Int): Float = throwForWidgetMethods()
    override fun calculateWidgetX(left: Int): Float = throwForWidgetMethods()

    private fun throwForWidgetMethods(): Nothing = throw AssertionError("No widgets in dock")
}
