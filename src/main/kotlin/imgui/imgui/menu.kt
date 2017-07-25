package imgui.imgui

import gli.has
import glm_.f
import glm_.glm
import glm_.i
import glm_.vec2.Vec2
import imgui.*
import imgui.ImGui.alignFirstTextHeightToWidgets
import imgui.ImGui.begin
import imgui.ImGui.beginGroup
import imgui.ImGui.contentRegionAvail
import imgui.ImGui.currentWindow
import imgui.ImGui.end
import imgui.ImGui.endGroup
import imgui.ImGui.isHovered
import imgui.ImGui.popClipRect
import imgui.ImGui.popId
import imgui.ImGui.popStyleColor
import imgui.ImGui.popStyleVar
import imgui.ImGui.pushClipRect
import imgui.ImGui.pushId
import imgui.ImGui.pushStyleColor
import imgui.ImGui.pushStyleVar
import imgui.ImGui.renderCollapseTriangle
import imgui.ImGui.sameLine
import imgui.ImGui.selectable
import imgui.ImGui.setNextWindowPos
import imgui.ImGui.setNextWindowSize
import imgui.internal.LayoutType
import imgui.internal.Rect
import imgui.internal.isPointInTriangle
import imgui.Context as g

/** Menu    */
interface imgui_menu {


    /** create and append to a full screen menu-bar. only call EndMainMenuBar() if this returns true!   */
    fun beginMainMenuBar(): Boolean {

        setNextWindowPos(Vec2())
        setNextWindowSize(Vec2(IO.displaySize.x, g.fontBaseSize + Style.framePadding.y * 2f))
        pushStyleVar(StyleVar.WindowRounding, 0f)
        pushStyleVar(StyleVar.WindowMinSize, Vec2())
        val flags = WindowFlags.NoTitleBar or WindowFlags.NoResize or WindowFlags.NoMove or WindowFlags.NoScrollbar or
                WindowFlags.NoSavedSettings or WindowFlags.MenuBar
        if (!begin("##MainMenuBar", null, flags) || !beginMenuBar()) {
            end()
            popStyleVar(2)
            return false
        }
        g.currentWindow!!.dc.menuBarOffsetX += Style.displaySafeAreaPadding.x
        return true
    }

    fun endMainMenuBar() {
        endMenuBar()
        end()
        popStyleVar(2)
    }

    /** append to menu-bar of current window (requires ImGuiWindowFlags_MenuBar flag set). only call EndMenuBar() if
     *  this returns true!  */
    fun beginMenuBar(): Boolean {

        val window = currentWindow
        if (window.skipItems) return false
        if (window.flags hasnt WindowFlags.MenuBar) return false

        assert(!window.dc.menuBarAppending)
        beginGroup() // Save position
        pushId("##menubar")
        val rect = Rect(window.menuBarRect())
        pushClipRect(Vec2(glm.floor(rect.min.x + 0.5f), glm.floor(rect.min.y + window.borderSize + 0.5f)),
                Vec2(glm.floor(rect.max.x + 0.5f), glm.floor(rect.max.y + 0.5f)), false)
        window.dc.cursorPos = Vec2(rect.min.x + window.dc.menuBarOffsetX, rect.min.y)// + g.Style.FramePadding.y);
        window.dc.layoutType = LayoutType.Horizontal
        window.dc.menuBarAppending = true
        alignFirstTextHeightToWidgets()
        return true
    }

    fun endMenuBar() {

        val window = currentWindow
        if (window.skipItems) return

        assert(window.flags has WindowFlags.MenuBar)
        assert(window.dc.menuBarAppending)
        popClipRect()
        popId()
        window.dc.menuBarOffsetX = window.dc.cursorPos.x - window.menuBarRect().min.x
        window.dc.groupStack.last().advanceCursor = false
        endGroup()
        window.dc.layoutType = LayoutType.Vertical
        window.dc.menuBarAppending = false
    }

    /** create a sub-menu entry. only call EndMenu() if this returns true!  */
    fun beginMenu(label: String, enabled: Boolean = true): Boolean {

        val window = currentWindow
        if (window.skipItems) return false

        val id = window.getId(label)

        val labelSize = calcTextSize(label, true)
        val backedFocusedWindow = g.focusedWindow!!

        var pressed = false
        var menuIsOpen = isPopupOpen(id)
        val menusetIsOpen = window.flags hasnt WindowFlags.Popup && g.openPopupStack.size > g.currentPopupStack.size &&
                g.openPopupStack[g.currentPopupStack.size].parentMenuSet == window.getId("##menus")
        if (menusetIsOpen)
            g.focusedWindow = window

        // The reference position stored in popupPos will be used by Begin() to find a suitable position for the child menu (using FindBestPopupWindowPos).
        val popupPos = Vec2()
        val pos = Vec2(window.dc.cursorPos)
        if (window.dc.layoutType == LayoutType.Horizontal) {
            popupPos.put(pos.x - window.windowPadding.x, pos.y - Style.framePadding.y + window.menuBarHeight())
            window.dc.cursorPos.x += (Style.itemSpacing.x * 0.5f).i.f
            pushStyleVar(StyleVar.ItemSpacing, Style.itemSpacing * 2f)
            val w = labelSize.x
            val flags = SelectableFlags.Menu or SelectableFlags.DontClosePopups or if (enabled) 0 else SelectableFlags.Disabled.i
            pressed = selectable(label, menuIsOpen, flags, Vec2(w, 0f))
            popStyleVar()
            sameLine()
            window.dc.cursorPos.x += (Style.itemSpacing.x * 0.5f).i.f
        } else {
            popupPos.put(pos.x, pos.y - Style.windowPadding.y)
            val w = window.menuColumns.declColumns(labelSize.x, 0f, (g.fontSize * 1.2f).i.f) // Feedback to next frame
            val extraW = glm.max(0f, contentRegionAvail.x - w)
            val flags = SelectableFlags.Menu or SelectableFlags.DontClosePopups or SelectableFlags.DrawFillAvailWidth
            pressed = selectable(label, menuIsOpen, flags or if (enabled) 0 else SelectableFlags.Disabled.i, Vec2(w, 0f))
            if (!enabled) pushStyleColor(Col.Text, Style.colors[Col.TextDisabled])
            renderCollapseTriangle(pos + Vec2(window.menuColumns.pos[2] + extraW + g.fontSize * 0.2f, 0f), false)
            if (!enabled) popStyleColor()
        }

        val hovered = enabled && isHovered(window.dc.lastItemRect, id)
        if (menusetIsOpen)
            g.focusedWindow = backedFocusedWindow

        var wantOpen = false
        var wantClose = false
        if (window.flags has (WindowFlags.Popup or WindowFlags.ChildMenu)) {
            /*  Implement http://bjk5.com/post/44698559168/breaking-down-amazons-mega-dropdown to avoid using timers,
                so menus feels more reactive.             */
            var movingWithinOpenedTriangle = false
            if (g.hoveredWindow === window && g.openPopupStack.size > g.currentPopupStack.size &&
                    g.openPopupStack[g.currentPopupStack.size].parentWindow === window)

                g.openPopupStack[g.currentPopupStack.size].window?.let {
                    val nextWindowRect = it.rect()
                    val ta = IO.mousePos - IO.mouseDelta
                    val tb = if (window.pos.x < it.pos.x) nextWindowRect.tl else nextWindowRect.tr
                    val tc = if (window.pos.x < it.pos.x) nextWindowRect.bl else nextWindowRect.br
                    val extra = glm.clamp(glm.abs(ta.x - tb.x) * 0.3f, 5f, 30f) // add a bit of extra slack.
                    ta.x += if (window.pos.x < it.pos.x) -0.5f else +0.5f   // to avoid numerical issues
                    /*  triangle is maximum 200 high to limit the slope and the bias toward large sub-menus
                        FIXME: Multiply by fb_scale?                     */
                    tb.y = ta.y + glm.max((tb.y - extra) - ta.y, -100f)
                    tc.y = ta.y + glm.min((tc.y + extra) - ta.y, +100f)
                    movingWithinOpenedTriangle = isPointInTriangle(IO.mousePos, ta, tb, tc)
                    //window->DrawList->PushClipRectFullScreen(); window->DrawList->AddTriangleFilled(ta, tb, tc, movingWithinOpenedTriangle ? IM_COL32(0,128,0,128) : IM_COL32(128,0,0,128)); window->DrawList->PopClipRect(); // Debug
                }

            wantClose = (menuIsOpen && !hovered && g.hoveredWindow === window && g.hoveredIdPreviousFrame != 0 &&
                    g.hoveredIdPreviousFrame != id && !movingWithinOpenedTriangle)
            wantOpen = (!menuIsOpen && hovered && !movingWithinOpenedTriangle) || (!menuIsOpen && hovered && pressed)
        } else if (menuIsOpen && pressed && menusetIsOpen) {    // menu-bar: click open menu to close
            wantClose = true
            wantOpen = false
            menuIsOpen = false
        } else if (pressed || (hovered && menusetIsOpen && !menuIsOpen)) // menu-bar: first click to open, then hover to open others
            wantOpen = true
        /*  explicitly close if an open menu becomes disabled, facilitate users code a lot in pattern such as
            'if (BeginMenu("options", has_object)) { ..use object.. }'         */
        if (!enabled)
            wantClose = true
        if (wantClose && isPopupOpen(id))
            closePopupToLevel(g.currentPopupStack.size)

        if (!menuIsOpen && wantOpen && g.openPopupStack.size > g.currentPopupStack.size) {
            // Don't recycle same menu level in the same frame, first close the other menu and yield for a frame.
            openPopup(label)
            return false
        }

        menuIsOpen | = want_open
        if (wantOpen)
            OpenPopup(label)

        if (menuIsOpen) {
            SetNextWindowPos(popupPos, ImGuiSetCond_Always)
            ImGuiWindowFlags flags = ImGuiWindowFlags_ShowBorders |((window->Flags & (ImGuiWindowFlags_Popup|ImGuiWindowFlags_ChildMenu)) ? ImGuiWindowFlags_ChildMenu|ImGuiWindowFlags_ChildWindow : ImGuiWindowFlags_ChildMenu)
            menuIsOpen = BeginPopupEx(label, flags) // menuIsOpen can be 'false' when the popup is completely clipped (e.g. zero size display)
        }

        return menuIsOpen
    }
//    IMGUI_API void          EndMenu();
//    IMGUI_API bool          MenuItem(const char* label, const char* shortcut = NULL, bool selected = false, bool enabled = true);  // return true when activated. shortcuts are displayed for convenience but not processed by ImGui at the moment
//    IMGUI_API bool          MenuItem(const char* label, const char* shortcut, bool* p_selected, bool enabled = true);              // return true when activated + toggle (*p_selected) if p_selected != NULL
}