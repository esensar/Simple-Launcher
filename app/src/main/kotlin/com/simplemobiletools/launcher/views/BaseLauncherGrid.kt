package com.simplemobiletools.launcher.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.widget.RelativeLayout
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.extensions.config
import com.simplemobiletools.launcher.extensions.getDrawableForPackageName
import com.simplemobiletools.launcher.helpers.*
import com.simplemobiletools.launcher.models.HomeScreenGridItem
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlin.math.abs
import kotlin.math.min

abstract class BaseLauncherGrid(context: Context, attrs: AttributeSet, defStyle: Int) : RelativeLayout(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    protected var iconMargin = context.resources.getDimension(R.dimen.icon_side_margin).toInt()
    private var roundedCornerRadius = context.resources.getDimension(R.dimen.activity_margin)
    private var dragShadowCirclePaint: Paint
    protected var draggedItem: HomeScreenGridItem? = null
    private var isFirstDraw = true
    protected var iconSize = 0
    private var drawDebugGrid = false
    private var drawDebugClickableAreas = false
    private var debugGridPaint: Paint
    private var debugClickableAreasPaint: Paint

    protected var columnCount = context.config.homeColumnCount
    protected var rowCount = context.config.homeRowCount
    protected var cellXCoords = ArrayList<Int>(columnCount)
    protected var cellYCoords = ArrayList<Int>(rowCount)
    var cellWidth = 0
    var cellHeight = 0

    var resizedInThisFrame = false

    // apply fake margins at the home screen. Real ones would cause the icons be cut at dragging at screen sides
    var sideMargins = Rect()

    protected var gridItems = ArrayList<HomeScreenGridItem>()
    protected var gridCenters = ArrayList<Pair<Int, Int>>()
    protected var draggedItemCurrentCoords = Pair(-1, -1)

    init {
        dragShadowCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.resources.getColor(R.color.light_grey_stroke)
            strokeWidth = context.resources.getDimension(R.dimen.small_margin)
            style = Paint.Style.STROKE
        }

        debugGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.resources.getColor(R.color.md_red)
            strokeWidth = context.resources.getDimension(R.dimen.small_margin)
            style = Paint.Style.STROKE
        }

        debugClickableAreasPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.resources.getColor(R.color.md_blue)
            strokeWidth = context.resources.getDimension(R.dimen.small_margin)
            style = Paint.Style.STROKE
        }

        val sideMargin = context.resources.getDimension(R.dimen.normal_margin).toInt()
        sideMargins.apply {
            top = context.statusBarHeight
            bottom = context.navigationBarHeight
            left = sideMargin
            right = sideMargin
        }
    }

    fun resizeGrid(newRowCount: Int, newColumnCount: Int) {
        if (columnCount != newColumnCount || rowCount != newRowCount){
            rowCount = newRowCount
            columnCount = newColumnCount
            cellXCoords = ArrayList(columnCount)
            cellYCoords = ArrayList(rowCount)
            resizedInThisFrame = true
            redrawGrid()
        }
    }

    fun itemDraggingStarted(draggedGridItem: HomeScreenGridItem) {
        draggedItem = draggedGridItem
        if (draggedItem!!.drawable == null) {
            draggedItem!!.drawable = context.getDrawableForPackageName(draggedGridItem.packageName)
        }

        redrawGrid()
    }

    fun draggedItemMoved(x: Int, y: Int) {
        if (draggedItem == null) {
            return
        }

        if (draggedItemCurrentCoords.first == -1 && draggedItemCurrentCoords.second == -1 && draggedItem != null) {
            if (draggedItem!!.type == ITEM_TYPE_WIDGET) {
                onDraggedWidgetMoved(x, y)
            }
        }

        draggedItemCurrentCoords = Pair(x, y)
        redrawGrid()
    }

    open fun onDraggedWidgetMoved(x: Int, y: Int) {}

    // figure out at which cell was the item dropped, if it is empty
    open fun itemDraggingStopped() {
        if (draggedItem == null) {
            return
        }

        when (draggedItem!!.type) {
            ITEM_TYPE_ICON, ITEM_TYPE_SHORTCUT -> addAppIconOrShortcut()
            ITEM_TYPE_WIDGET -> addWidget()
        }
    }

    // convert stuff like 102x192 to grid cells like 0x1
    protected fun getClosestGridCells(center: Pair<Int, Int>): Pair<Int, Int>? {
        cellXCoords.forEachIndexed { xIndex, xCell ->
            cellYCoords.forEachIndexed { yIndex, yCell ->
                if (xCell + cellWidth / 2 == center.first && yCell + cellHeight / 2 == center.second) {
                    return Pair(xIndex, yIndex)
                }
            }
        }

        return null
    }

    protected fun redrawGrid() {
        post {
            setWillNotDraw(false)
            invalidate()
        }
    }

    protected fun getFakeWidth() = width - sideMargins.left - sideMargins.right

    protected fun getFakeHeight() = height - sideMargins.top - sideMargins.bottom

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas?) {
        if (canvas == null) {
            return
        }

        super.onDraw(canvas)
        if (cellXCoords.isEmpty()) {
            fillCellSizes()
        }

        val extraXMargin = if (cellWidth > cellHeight) {
            (cellWidth - cellHeight) / 2
        } else {
            0
        }
        val extraYMargin = if (cellHeight > cellWidth) {
            (cellHeight - cellWidth) / 2
        } else {
            0
        }
        gridItems.filter { it.drawable != null && (it.type == ITEM_TYPE_ICON || it.type == ITEM_TYPE_SHORTCUT) }.forEach { item ->
            Log.d("TESTENSAR", "onDraw ($this) = attempt to draw $item")
            if (item.outOfBounds()) {
                Log.d("TESTENSAR", "onDraw ($this) = OUT OF BOUNDS :(")
                return@forEach
            }

            if (item.id != draggedItem?.id) {
                drawGridIcon(item, canvas, extraXMargin, extraYMargin)
            }
        }

        if (isFirstDraw || resizedInThisFrame) {
            gridItems.filter { it.type == ITEM_TYPE_WIDGET }.forEach { item ->
                bindWidget(item, isFirstDraw)
            }
            resizedInThisFrame = false
        }

        if (draggedItem != null && draggedItemCurrentCoords.first != -1 && draggedItemCurrentCoords.second != -1) {
            if (draggedItem!!.type == ITEM_TYPE_ICON || draggedItem!!.type == ITEM_TYPE_SHORTCUT) {
                // draw a circle under the current cell
                val center = gridCenters.minBy {
                    abs(it.first - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.second - draggedItemCurrentCoords.second + sideMargins.top)
                }

                val gridCells = getClosestGridCells(center)
                if (gridCells != null) {
                    val (shadowX, shadowY) = getIconCenter(gridCells, extraXMargin, extraYMargin)

                    canvas.drawCircle(shadowX, shadowY, iconSize / 2f, dragShadowCirclePaint)
                }

                // show the app icon itself at dragging, move it above the finger a bit to make it visible
                val drawableX = (draggedItemCurrentCoords.first - iconSize / 1.5f).toInt()
                val drawableY = (draggedItemCurrentCoords.second - iconSize / 1.2f).toInt()
                draggedItem!!.drawable!!.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)
                draggedItem!!.drawable!!.draw(canvas)
            } else if (draggedItem!!.type == ITEM_TYPE_WIDGET) {
                // at first draw we are loading the widget from the database at some exact spot, not dragging it
                if (!isFirstDraw) {
                    val center = gridCenters.minBy {
                        Math.abs(it.first - draggedItemCurrentCoords.first + sideMargins.left) + Math.abs(it.second - draggedItemCurrentCoords.second + sideMargins.top)
                    }

                    val gridCells = getClosestGridCells(center)
                    if (gridCells != null) {
                        val widgetRect = getWidgetOccupiedRect(gridCells)
                        val leftSide = widgetRect.left * cellWidth + sideMargins.left + iconMargin.toFloat()
                        val topSide = widgetRect.top * cellHeight + sideMargins.top + iconMargin.toFloat()
                        val rightSide = leftSide + draggedItem!!.getWidthInCells() * cellWidth - sideMargins.right - iconMargin.toFloat()
                        val bottomSide = topSide + draggedItem!!.getHeightInCells() * cellHeight - sideMargins.top
                        canvas.drawRoundRect(leftSide, topSide, rightSide, bottomSide, roundedCornerRadius, roundedCornerRadius, dragShadowCirclePaint)
                    }

                    // show the widget preview itself at dragging
                    val drawable = draggedItem!!.drawable!!
                    val aspectRatio = drawable.minimumHeight / drawable.minimumWidth.toFloat()
                    val drawableX = (draggedItemCurrentCoords.first - drawable.minimumWidth / 2f).toInt()
                    val drawableY = (draggedItemCurrentCoords.second - drawable.minimumHeight / 3f).toInt()
                    val drawableWidth = draggedItem!!.getWidthInCells() * cellWidth - iconMargin * (draggedItem!!.getWidthInCells() - 1)
                    drawable.setBounds(
                        drawableX,
                        drawableY,
                        drawableX + drawableWidth,
                        (drawableY + drawableWidth * aspectRatio).toInt()
                    )
                    drawable.draw(canvas)
                }
            }
        }

        if (drawDebugGrid) {
            val usableWidth = getFakeWidth().toFloat()
            val usableHeight = getFakeHeight().toFloat()
            cellXCoords.forEach { x ->
                canvas.drawLine(x.toFloat() + sideMargins.left, sideMargins.top.toFloat(), x + sideMargins.left + 1f, sideMargins.top + usableHeight, debugGridPaint)
            }
            canvas.drawLine(sideMargins.left + usableWidth, sideMargins.top.toFloat(), sideMargins.left+ usableWidth + 1f, sideMargins.top + usableHeight, debugGridPaint)
            cellYCoords.forEach { y ->
                canvas.drawLine(sideMargins.left.toFloat(), y.toFloat() + sideMargins.top, sideMargins.left + usableWidth, y + sideMargins.top + 1f, debugGridPaint)
            }
            canvas.drawLine(sideMargins.left.toFloat(), usableHeight + sideMargins.top, sideMargins.left + usableWidth, usableHeight + sideMargins.top + 1f, debugGridPaint)
        }

        if (drawDebugClickableAreas) {
            gridItems.forEach {
                if (it.outOfBounds()) {
                    return@forEach
                }
                val clickableRect = getClickableRect(it)
                canvas.drawRect(clickableRect, debugClickableAreasPaint)
            }
        }

        isFirstDraw = false
    }

    private fun fillCellSizes() {
        cellWidth = getFakeWidth() / columnCount
        cellHeight = getFakeHeight() / rowCount
        iconSize = min(cellWidth, cellHeight) - 2 * iconMargin
        for (i in 0 until columnCount) {
            cellXCoords.add(i, i * cellWidth)
        }

        for (i in 0 until rowCount) {
            cellYCoords.add(i, i * cellHeight)
        }

        cellXCoords.forEach { x ->
            cellYCoords.forEach { y ->
                gridCenters.add(Pair(x + cellWidth / 2, y + cellHeight / 2))
            }
        }
    }

    // get the clickable area around the icon, it includes text too
    fun getClickableRect(item: HomeScreenGridItem): Rect {
        if (cellXCoords.isEmpty()) {
            fillCellSizes()
        }

        val clickableLeft = item.left * cellWidth + sideMargins.left
        val clickableTop = cellYCoords[item.top] + iconSize / 3 + sideMargins.top
        return Rect(clickableLeft, clickableTop, clickableLeft + cellWidth, clickableTop + cellHeight - iconSize / 3)
    }

    fun isClickingGridItem(x: Int, y: Int): HomeScreenGridItem? {
        for (gridItem in gridItems) {
            if (gridItem.outOfBounds()) {
                continue
            }

            if (gridItem.type == ITEM_TYPE_ICON || gridItem.type == ITEM_TYPE_SHORTCUT) {
                val rect = getClickableRect(gridItem)
                if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                    return gridItem
                }
            } else if (gridItem.type == ITEM_TYPE_WIDGET) {
                val left = calculateWidgetX(gridItem.left)
                val top = calculateWidgetY(gridItem.top)
                val right = left + gridItem.getWidthInCells() * cellWidth
                val bottom = top + gridItem.getHeightInCells() * cellHeight

                if (x >= left && x <= right && y >= top && y <= bottom) {
                    return gridItem
                }
            }
        }

        return null
    }

    protected abstract fun getIconCenter(cell: Pair<Int, Int>, extraXMargin: Int, extraYMargin: Int): Pair<Float, Float>
    protected abstract fun drawGridIcon(item: HomeScreenGridItem, canvas: Canvas, extraXMargin: Int, extraYMargin: Int)
    protected abstract fun getWidgetOccupiedRect(item: Pair<Int, Int>): Rect
    protected abstract fun bindWidget(item: HomeScreenGridItem, isInitialDrawAfterLaunch: Boolean)
    protected abstract fun addAppIconOrShortcut()
    protected abstract fun addWidget()
    protected abstract fun calculateWidgetY(top: Int): Float
    protected abstract fun calculateWidgetX(left: Int): Float

    fun intoViewSpaceCoords(screenSpaceX: Float, screenSpaceY: Float): Pair<Float, Float> {
        val viewLocation = IntArray(2)
        getLocationOnScreen(viewLocation)
        val x = screenSpaceX - viewLocation[0]
        val y = screenSpaceY - viewLocation[1]
        return Pair(x, y)
    }

    protected fun HomeScreenGridItem.outOfBounds(): Boolean {
        return (left >= cellXCoords.size || right >= cellXCoords.size || top >= cellYCoords.size || bottom >= cellYCoords.size)
    }
}
