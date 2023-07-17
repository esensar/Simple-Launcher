package com.simplemobiletools.launcher.views

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Size
import android.util.SizeF
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isSPlus
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.activities.MainActivity
import com.simplemobiletools.launcher.extensions.config
import com.simplemobiletools.launcher.extensions.getDrawableForPackageName
import com.simplemobiletools.launcher.extensions.homeScreenGridItemsDB
import com.simplemobiletools.launcher.helpers.*
import com.simplemobiletools.launcher.models.HomeScreenGridItem
import kotlinx.android.synthetic.main.activity_main.view.*

class HomeScreenGrid(context: Context, attrs: AttributeSet, defStyle: Int) : BaseLauncherGrid(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private var textPaint: TextPaint
    private var labelSideMargin = context.resources.getDimension(R.dimen.small_margin).toInt()
    private var resizedWidget: HomeScreenGridItem? = null

    private var widgetViews = ArrayList<MyAppWidgetHostView>()

    val appWidgetHost = MyAppWidgetHost(context, WIDGET_HOST_ID)
    private val appWidgetManager = AppWidgetManager.getInstance(context)

    init {
        textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = context.resources.getDimension(R.dimen.smaller_text_size)
            setShadowLayer(2f, 0f, 0f, Color.BLACK)
        }

        sideMargins.apply {
            bottom = 0
        }

        fetchGridItems()
    }

    fun fetchGridItems() {
        ensureBackgroundThread {
            val providers = appWidgetManager.installedProviders
            gridItems = context.homeScreenGridItemsDB.getAllItems() as ArrayList<HomeScreenGridItem>
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

                item.providerInfo = providers.firstOrNull { it.provider.className == item.className }
            }

            redrawGrid()
        }
    }

    override fun removeItem(item: HomeScreenGridItem) {
        ensureBackgroundThread {
            removeItemFromHomeScreen(item)
            post {
                removeView(widgetViews.firstOrNull { it.tag == item.widgetId })
            }

            gridItems.removeIf { it.id == item.id }
            redrawGrid()
        }
    }

    private fun removeItemFromHomeScreen(item: HomeScreenGridItem) {
        ensureBackgroundThread {
            if (item.id != null) {
                context.homeScreenGridItemsDB.deleteById(item.id!!)
            }

            if (item.type == ITEM_TYPE_WIDGET) {
                appWidgetHost.deleteAppWidgetId(item.widgetId)
            }
        }
    }

    // figure out at which cell was the item dropped, if it is empty
    override fun itemDraggingStopped(): Boolean {
        widgetViews.forEach {
            it.hasLongPressed = false
        }

        return super.itemDraggingStopped()
    }

    override fun drawGridIcon(item: HomeScreenGridItem, canvas: Canvas, extraXMargin: Int, extraYMargin: Int) {
        val drawableX = cellXCoords[item.left] + iconMargin + extraXMargin + sideMargins.left
        val drawableY = cellYCoords[item.top] + iconMargin + extraYMargin + sideMargins.top
        item.drawable!!.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)

        if (item.id != draggedItem?.id && item.title.isNotEmpty()) {
            val textX = cellXCoords[item.left].toFloat() + labelSideMargin + sideMargins.left
            val textY = cellYCoords[item.top].toFloat() + iconSize + iconMargin + extraYMargin + labelSideMargin + sideMargins.top
            val staticLayout = StaticLayout.Builder
                .obtain(item.title, 0, item.title.length, textPaint, cellWidth - 2 * labelSideMargin)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()

            canvas.save()
            canvas.translate(textX, textY)
            staticLayout.draw(canvas)
            canvas.restore()
        }

        item.drawable!!.draw(canvas)
    }

    override fun getIconCenter(cell: Pair<Int, Int>, extraXMargin: Int, extraYMargin: Int): Pair<Float, Float> {
        val x = cellXCoords[cell.first] + iconMargin + iconSize / 2f + extraXMargin + sideMargins.left
        val y = cellYCoords[cell.second] + iconMargin + iconSize / 2f + extraYMargin + sideMargins.top
        return Pair(x, y)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun widgetLongPressed(item: HomeScreenGridItem) {
        resizedWidget = item
        redrawGrid()

        val widgetView = widgetViews.firstOrNull { it.tag == resizedWidget!!.widgetId }
        resize_frame.beGone()
        if (widgetView != null) {
            val viewX = widgetView.x.toInt()
            val viewY = widgetView.y.toInt()
            val frameRect = Rect(viewX, viewY, viewX + widgetView.width, viewY + widgetView.height)
            val otherGridItems = gridItems.filter { it.widgetId != item.widgetId }.toMutableList() as ArrayList<HomeScreenGridItem>
            resize_frame.updateFrameCoords(frameRect, cellWidth, cellHeight, sideMargins, item, otherGridItems)
            resize_frame.beVisible()
            resize_frame.z = 1f     // make sure the frame isnt behind the widget itself
            resize_frame.onClickListener = {
                hideResizeLines()
            }

            resize_frame.onResizeListener = { cellsRect ->
                item.left = cellsRect.left
                item.top = cellsRect.top
                item.right = cellsRect.right
                item.bottom = cellsRect.bottom
                updateWidgetPositionAndSize(widgetView, item)
                ensureBackgroundThread {
                    context.homeScreenGridItemsDB.updateItemPosition(cellsRect.left, cellsRect.top, cellsRect.right, cellsRect.bottom, item.id!!)
                }
            }

            widgetView.ignoreTouches = true
            widgetView.setOnTouchListener { v, event ->
                resize_frame.onTouchEvent(event)
                return@setOnTouchListener true
            }
        }
    }

    fun hideResizeLines() {
        if (resizedWidget == null) {
            return
        }

        resize_frame.beGone()
        widgetViews.firstOrNull { it.tag == resizedWidget!!.widgetId }?.apply {
            ignoreTouches = false
            setOnTouchListener(null)
        }
        resizedWidget = null
    }

    override fun addAppIconOrShortcut() {
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

    override fun addWidget() {
        val center = gridCenters.minBy {
            Math.abs(it.first - draggedItemCurrentCoords.first + sideMargins.left) + Math.abs(it.second - draggedItemCurrentCoords.second + sideMargins.top)
        }

        val gridCells = getClosestGridCells(center)
        if (gridCells != null) {
            val widgetRect = getWidgetOccupiedRect(gridCells)
            val widgetTargetCells = ArrayList<Pair<Int, Int>>()
            for (xCell in widgetRect.left..widgetRect.right) {
                for (yCell in widgetRect.top..widgetRect.bottom) {
                    widgetTargetCells.add(Pair(xCell, yCell))
                }
            }

            var areAllCellsEmpty = true
            gridItems.filter { it.id != draggedItem?.id }.forEach { item ->
                for (xCell in item.left..item.right) {
                    for (yCell in item.top..item.bottom) {
                        val cell = Pair(xCell, yCell)
                        val isAnyCellOccupied = widgetTargetCells.contains(cell)
                        if (isAnyCellOccupied) {
                            areAllCellsEmpty = false
                            return@forEach
                        }
                    }
                }
            }

            if (areAllCellsEmpty) {
                val widgetItem = draggedItem!!.copy()
                widgetItem.apply {
                    left = widgetRect.left
                    top = widgetRect.top
                    right = widgetRect.right
                    bottom = widgetRect.bottom
                }

                ensureBackgroundThread {
                    // store the new widget at creating it, else just move the existing one
                    if (widgetItem.id == null) {
                        val itemId = context.homeScreenGridItemsDB.insert(widgetItem)
                        widgetItem.id = itemId
                        post {
                            bindWidget(widgetItem, false)
                        }
                    } else {
                        context.homeScreenGridItemsDB.updateItemPosition(widgetItem.left, widgetItem.top, widgetItem.right, widgetItem.bottom, widgetItem.id!!)
                        val widgetView = widgetViews.firstOrNull { it.tag == widgetItem.widgetId }
                        if (widgetView != null) {
                            post {
                                widgetView.x = calculateWidgetX(widgetItem.left)
                                widgetView.y = calculateWidgetY(widgetItem.top)
                                widgetView.beVisible()
                            }
                        }

                        gridItems.firstOrNull { it.id == widgetItem.id }?.apply {
                            left = widgetItem.left
                            right = widgetItem.right
                            top = widgetItem.top
                            bottom = widgetItem.bottom
                        }
                    }
                }
            } else {
                performHapticFeedback()
                widgetViews.firstOrNull { it.tag == draggedItem?.widgetId }?.apply {
                    post {
                        beVisible()
                    }
                }
            }
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        redrawGrid()
    }

    override fun bindWidget(item: HomeScreenGridItem, isInitialDrawAfterLaunch: Boolean) {
        val activity = context as MainActivity
        val appWidgetProviderInfo = item.providerInfo ?: appWidgetManager!!.installedProviders.firstOrNull { it.provider.className == item.className }
        if (appWidgetProviderInfo != null) {
            val appWidgetId = appWidgetHost.allocateAppWidgetId()
            activity.handleWidgetBinding(appWidgetManager, appWidgetId, appWidgetProviderInfo) { canBind ->
                if (canBind) {
                    if (appWidgetProviderInfo.configure != null && !isInitialDrawAfterLaunch) {
                        activity.handleWidgetConfigureScreen(appWidgetHost, appWidgetId) { success ->
                            if (success) {
                                placeAppWidget(appWidgetId, appWidgetProviderInfo, item)
                            } else {
                                removeItemFromHomeScreen(item)
                            }
                        }
                    } else {
                        placeAppWidget(appWidgetId, appWidgetProviderInfo, item)
                    }
                } else {
                    removeItemFromHomeScreen(item)
                }
            }
        }
    }

    private fun placeAppWidget(appWidgetId: Int, appWidgetProviderInfo: AppWidgetProviderInfo, item: HomeScreenGridItem) {
        item.widgetId = appWidgetId
        // we have to pass the base context here, else there will be errors with the themes
        val widgetView = appWidgetHost.createView((context as MainActivity).baseContext, appWidgetId, appWidgetProviderInfo) as MyAppWidgetHostView
        widgetView.tag = appWidgetId
        widgetView.setAppWidget(appWidgetId, appWidgetProviderInfo)
        widgetView.longPressListener = { x, y ->
            val activity = context as? MainActivity
            if (activity?.isAllAppsFragmentExpanded() == false) {
                activity.showHomeIconMenu(x, widgetView.y, item, false)
                performHapticFeedback()
            }
        }

        widgetView.onIgnoreInterceptedListener = {
            hideResizeLines()
        }

        val widgetSize = updateWidgetPositionAndSize(widgetView, item)
        addView(widgetView, widgetSize.width, widgetSize.height)
        widgetViews.add(widgetView)

        // remove the drawable so that it gets refreshed on long press
        item.drawable = null
        gridItems.add(item)
    }

    private fun updateWidgetPositionAndSize(widgetView: AppWidgetHostView, item: HomeScreenGridItem): Size {
        widgetView.x = calculateWidgetX(item.left)
        widgetView.y = calculateWidgetY(item.top)
        val widgetWidth = item.getWidthInCells() * cellWidth
        val widgetHeight = item.getHeightInCells() * cellHeight

        val density = context.resources.displayMetrics.density
        val widgetDpWidth = (widgetWidth / density).toInt()
        val widgetDpHeight = (widgetHeight / density).toInt()

        if (isSPlus()) {
            val sizes = listOf(SizeF(widgetDpWidth.toFloat(), widgetDpHeight.toFloat()))
            widgetView.updateAppWidgetSize(Bundle(), sizes)
        } else {
            widgetView.updateAppWidgetSize(Bundle(), widgetDpWidth, widgetDpHeight, widgetDpWidth, widgetDpHeight)
        }

        widgetView.layoutParams?.width = widgetWidth
        widgetView.layoutParams?.height = widgetHeight
        return Size(widgetWidth, widgetHeight)
    }

    override fun calculateWidgetX(left: Int) = left * cellWidth + sideMargins.left.toFloat()

    override fun calculateWidgetY(top: Int) = top * cellHeight + sideMargins.top.toFloat()

    // convert stuff like 102x192 to grid cells like 0x1
    fun fragmentExpanded() {
        widgetViews.forEach {
            it.ignoreTouches = true
        }
    }

    fun fragmentCollapsed() {
        widgetViews.forEach {
            it.ignoreTouches = false
        }
    }

    // drag the center of the widget, not the top left corner
    override fun getWidgetOccupiedRect(item: Pair<Int, Int>): Rect {
        val left = item.first - Math.floor((draggedItem!!.getWidthInCells() - 1) / 2.0).toInt()
        val rect = Rect(left, item.second, left + draggedItem!!.getWidthInCells() - 1, item.second + draggedItem!!.getHeightInCells() - 1)
        if (rect.left < 0) {
            rect.right -= rect.left
            rect.left = 0
        } else if (rect.right > columnCount - 1) {
            val diff = rect.right - columnCount + 1
            rect.right -= diff
            rect.left -= diff
        }

        if (rect.top < 0) {
            rect.bottom -= rect.top
            rect.top = 0
        } else if (rect.bottom > rowCount - 1) {
            val diff = rect.bottom - rowCount + 1
            rect.bottom -= diff
            rect.top -= diff
        }

        return rect
    }
}
