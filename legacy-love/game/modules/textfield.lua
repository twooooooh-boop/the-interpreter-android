local Caret = require("modules.caret")
local utf8 = require("utf8")
local Keyboard = require("modules.keyboard")

local TextField = {}
TextField.__index = TextField
TextField.activeField = nil

function TextField.new(x, y, w, h, font, mode, editMode, selectionMode)

    local self = setmetatable({}, TextField)

    self.x, self.y, self.w, self.h = x, y, w, h

    self.font = font

    self.mode = mode or "horizontal"

    -------------------------------------------------
    -- SELECTION MODE
    -------------------------------------------------

    -- "pseudo" = current lightweight selection
    -- "full"   = shows copy/copy all popup

    self.selectionMode = selectionMode or "pseudo"
    self.editMode = editMode or "editable"

    -------------------------------------------------

    self.text = "asscheekasscheekasscheekasscheekasscheekasscheekasscheek"
    self.pos = 1

    self.blink = 0
    self.visible = true
    self.focused = false

    -------------------------------------------------
    -- SELECTION
    -------------------------------------------------

    self.selStart = nil
    self.selEnd = nil
    self.isSelecting = false

    -------------------------------------------------
    -- COPY UI
    -------------------------------------------------

    self.showSelectionMenu = false

    self.copyButton = nil
    self.copyAllButton = nil

    -------------------------------------------------
    -- SCROLLING
    -------------------------------------------------

    self.thumbY = 0
    self.thumbH = 0

    self.isDraggingThumb = false

    self.scrollX = 0
    self.scrollY = 0

    self.padding = 10
    self.buffer = 25

    self.lastTextLength = 0

    self.isDraggingScroll = false

    return self
end

function TextField:getTextDimensions()
    if self.mode == "vertical" then
        local widthLimit = self.w - (self.padding * 2)
        local width, lines = self.font:getWrap(self.text, widthLimit)
        return width, #lines * self.font:getHeight()
    else
        return self.font:getWidth(self.text), self.font:getHeight()
    end
end

function TextField:update(dt)
    if not self.focused then return end
    
    self.blink = self.blink + dt
    if self.blink >= 0.5 then
        self.blink = 0
        self.visible = not self.visible
    end

    local cx, cy = self:getCaretVisualPos()
    local lineH = self.font:getHeight()
    local viewW = self.w - (self.padding * 2)
    local viewH = self.h - (self.padding * 2)

    -- TRACKER: Only snap if the cursor moved or text changed
    local currentPos = self.pos
    local moved = (currentPos ~= self.lastPos)
    self.lastPos = currentPos 

    if moved then
        if self.mode == "horizontal" then
            local textW = self.font:getWidth(self.text)
            
            -- Snap logic: Keep caret in view
            if cx + self.scrollX > (viewW - self.buffer) then
                self.scrollX = (viewW - self.buffer) - cx
            elseif cx + self.scrollX < self.buffer then
                self.scrollX = self.buffer - cx
            end

            -- Don't allow scrolling into empty space if text is short
            if textW < viewW then 
                self.scrollX = 0 
            elseif self.scrollX > 0 then 
                self.scrollX = 0 
            end

        elseif self.mode == "vertical" then

            local _, totalH =
                self:getTextDimensions()

            -------------------------------------------------
            -- CARET TOP/BOTTOM IN VIEW
            -------------------------------------------------

            local caretTop =
                cy + self.scrollY

            local caretBottom =
                caretTop + lineH

            -------------------------------------------------
            -- SNAP DOWN
            -------------------------------------------------

            if caretBottom > viewH then

                self.scrollY =
                    self.scrollY
                    - (caretBottom - viewH)

            -------------------------------------------------
            -- SNAP UP
            -------------------------------------------------

            elseif caretTop < 0 then

                self.scrollY =
                    self.scrollY - caretTop
            end

            -------------------------------------------------
            -- CLAMP SCROLL
            -------------------------------------------------

            local minScroll =
                math.min(0, viewH - totalH)

            if self.scrollY < minScroll then
                self.scrollY = minScroll
            end

            if self.scrollY > 0 then
                self.scrollY = 0
            end
        end
    end
end

function TextField:getCaretVisualPos()
    local before = Caret.sub(self.text, 1, self.pos - 1)
    if self.mode == "horizontal" then
        return self.font:getWidth(before), 0
    else
        local widthLimit = self.w - (self.padding * 2)
        local _, lines = self.font:getWrap(before, widthLimit)
        local lastLineText = lines[#lines] or ""
        
        if before:sub(-1) == "\n" then
            return 0, #lines * self.font:getHeight()
        end
        
        local x = self.font:getWidth(lastLineText)
        local y = (#lines - 1) * self.font:getHeight()
        return x, y
    end
end

function TextField:getCharacterIndexFromMouse(mx, my)

    local localX = mx - (self.x + self.padding + self.scrollX)
    local localY = my - (self.y + self.padding + self.scrollY)

    localX = math.max(0, localX)
    localY = math.max(0, localY)

    local len = utf8.len(self.text) or 0

    if self.mode == "horizontal" then

        local bestPos = 1
        local bestDist = math.huge

        for i = 0, len do

            local before = Caret.sub(self.text, 1, i)
            local w = self.font:getWidth(before)

            local dist = math.abs(localX - w)

            if dist < bestDist then
                bestDist = dist
                bestPos = i + 1
            end
        end

        return bestPos
    end

    local widthLimit = self.w - (self.padding * 2)

    local currentLine = math.floor(localY / self.font:getHeight()) + 1

    local wrapped = {}

    local _, lines = self.font:getWrap(self.text, widthLimit)

    for i, line in ipairs(lines) do
        wrapped[i] = line
    end

    currentLine = math.max(1, math.min(currentLine, #wrapped))

    local charOffset = 0

    for i = 1, currentLine - 1 do
        charOffset = charOffset + (utf8.len(wrapped[i]) or 0)
    end

    local lineText = wrapped[currentLine] or ""

    local bestPos = 0
    local bestDist = math.huge

    local lineLen = utf8.len(lineText) or 0

    for i = 0, lineLen do

        local before = Caret.sub(lineText, 1, i)
        local w = self.font:getWidth(before)

        local dist = math.abs(localX - w)

        if dist < bestDist then
            bestDist = dist
            bestPos = i
        end
    end

    return charOffset + bestPos + 1
end

function TextField:handleInput(action, value)
    if not self.focused then return end
    if self.editMode ~= "editable" then return end

    if action == "insert" then
        Caret.insert(self, value)
    elseif action == "backspace" then
        Caret.backspace(self)
    end

    self.visible = true
    self.blink = 0
end

local function setClipboard(text)
    if love.system and love.system.setClipboardText then
        love.system.setClipboardText(text or "")
    end
end

function TextField:mousepressed(mx, my)

    -------------------------------------------------
    -- COPY BUTTON
    -------------------------------------------------

    if self.copyButton
    and mx >= self.copyButton.x
    and mx <= self.copyButton.x + self.copyButton.w
    and my >= self.copyButton.y
    and my <= self.copyButton.y + self.copyButton.h then

        if Caret.hasSelection(self) then

            local s = math.min(self.selStart, self.selEnd)
            local e = math.max(self.selStart, self.selEnd)

            local text = Caret.sub(self.text, s, e - 1)
            love.system.setClipboardText(text)
        end

        -------------------------------------------------
        -- RESET SELECTION STATE
        -------------------------------------------------

        self.selStart = nil
        self.selEnd = nil
        self.isSelecting = false
        self.showSelectionMenu = false

        return true
    end

    -------------------------------------------------
    -- COPY ALL BUTTON
    -------------------------------------------------

    if self.copyAllButton
    and mx >= self.copyAllButton.x
    and mx <= self.copyAllButton.x + self.copyAllButton.w
    and my >= self.copyAllButton.y
    and my <= self.copyAllButton.y + self.copyAllButton.h then

        love.system.setClipboardText(self.text)

        -------------------------------------------------
        -- RESET SELECTION STATE
        -------------------------------------------------

        self.selStart = nil
        self.selEnd = nil
        self.isSelecting = false
        self.showSelectionMenu = false

        return true
    end
    local insideField = mx >= self.x and mx <= self.x + self.w and 
                        my >= self.y and my <= self.y + self.h
    
    if self.mode == "vertical" then
        local _, totalH = self:getTextDimensions()
        local visibleH = self.h - self.padding * 2
        
        if totalH > visibleH then
            -- Check if clicking the scrollbar area (using a 20px hit-zone)
            if mx >= (self.x + self.w - 20) and mx <= (self.x + self.w) then
                self.isDraggingScroll = true
                -- Instant jump to click position (optional, but feels standard)
                self:mousemoved(mx, my, 0, 0) 
                return true
            end
        end
    end

    local insideKbd = Keyboard.isActive() and
                      mx >= Keyboard.x and mx <= Keyboard.x + Keyboard.w and
                      my >= Keyboard.y and my <= Keyboard.y + Keyboard.h

    if insideField then
        self.focused = true
        TextField.activeField = self

        if self.editMode == "editable" then
            Keyboard.setActive(true, function(a, v)
                self:handleInput(a, v)
            end)
        else
            Keyboard.setActive(false)
        end

        self.pos = self:getCharacterIndexFromMouse(mx, my)

        self.selStart = self.pos
        self.selEnd = self.pos
        self.isSelecting = true

        self.visible = true
        self.blink = 0

        return true        
    elseif not insideKbd then

        self.focused = false

        self.selStart = nil
        self.selEnd = nil
        self.isSelecting = false

        if TextField.activeField == self then
            TextField.activeField = nil
            Keyboard.setActive(false)
        end
    end

end

function TextField:mousemoved(mx, my, dx, dy)

    dy = dy or 0

    if self.isDraggingScroll and self.mode == "vertical" then

        local _, totalH = self:getTextDimensions()
        local visibleH = self.h - (self.padding * 2)

        local relativeY = my - (self.y + self.padding)

        local scrollPercent = relativeY / visibleH

        scrollPercent = math.max(0, math.min(1, scrollPercent))

        self.scrollY = -scrollPercent * (totalH - visibleH)
    end

    if self.isSelecting then

        local pos = self:getCharacterIndexFromMouse(mx, my)

        self.selEnd = pos
        self.pos = pos

        self.visible = true
        self.blink = 0
    end
end

function TextField:mousereleased(x, y, button)

    if button == 1 then

        self.isDraggingScroll = false
        self.isSelecting = false

        -------------------------------------------------
        -- SHOW SELECTION MENU
        -------------------------------------------------

        if self.selectionMode == "full"
        and Caret.hasSelection(self) then

            self.showSelectionMenu = true

        else
            self.showSelectionMenu = false
        end
    end
end

function TextField:wheelmoved(x, y)

    -- Get current mouse position
    local mx, my = love.mouse.getPosition()
    
    -- Check if the mouse is hovering over this specific field
    local isHovering = mx >= self.x and mx <= self.x + self.w and 
                       my >= self.y and my <= self.y + self.h

    if isHovering and self.mode == "vertical" then
        local _, totalH = self:getTextDimensions()
        local visibleH = self.h - (self.padding * 2)
        
        if totalH > visibleH then
            local scrollSpeed = 20 -- Adjust this number for faster/slower scrolling
            
            -- y is 1 for wheel up, -1 for wheel down
            self.scrollY = self.scrollY + (y * scrollSpeed)
            
            -- Clamp the results so we don't scroll into the void
            local minScroll = -(totalH - visibleH)
            if self.scrollY > 0 then self.scrollY = 0 end
            if self.scrollY < minScroll then self.scrollY = minScroll end
        end
    end
end

function TextField:drawSelectionMenu(drawX, drawY)

    if not self.showSelectionMenu then
        return
    end

    if not Caret.hasSelection(self) then
        return
    end

    -------------------------------------------------
    -- CARET POSITION
    -------------------------------------------------

    local cx, cy =
        self:getCaretVisualPos()

    local menuX =
        drawX + cx

    local menuY =
        drawY + cy
        - self.font:getHeight()
        - 14

    -------------------------------------------------
    -- DYNAMIC SIZING
    -------------------------------------------------

    local maxButtonW =
        math.min(120, self.w * 0.32)

    local buttonH =
        math.min(34, self.h * 0.18)

    local spacing = 8

    local copyText = "Copy"
    local copyAllText = "Copy All"

    local copyW =
        math.min(
            maxButtonW,
            self.font:getWidth(copyText) + 30
        )

    local copyAllW =
        math.min(
            maxButtonW,
            self.font:getWidth(copyAllText) + 30
        )

    -------------------------------------------------
    -- KEEP INSIDE TEXTFIELD
    -------------------------------------------------

    local totalW =
        copyW + spacing + copyAllW

    if menuX + totalW > self.x + self.w then
        menuX =
            (self.x + self.w)
            - totalW
            - self.padding
    end

    if menuX < self.x + self.padding then
        menuX = self.x + self.padding
    end

    if menuY < self.y + self.padding then
        menuY =
            self.y + self.padding
    end

    -------------------------------------------------
    -- STORE BUTTON RECTS
    -------------------------------------------------

    self.copyButton = {
        x = menuX,
        y = menuY,
        w = copyW,
        h = buttonH
    }

    self.copyAllButton = {
        x = menuX + copyW + spacing,
        y = menuY,
        w = copyAllW,
        h = buttonH
    }

    -------------------------------------------------
    -- DYNAMIC MENU FONT
    -------------------------------------------------

    local menuFontSize =
        math.floor(
            math.min(
                buttonH * 0.5,
                self.font:getHeight() * 0.9
            )
        )

    menuFontSize =
        math.max(10, menuFontSize)

    local menuFont =
        love.graphics.newFont(menuFontSize)

    love.graphics.setFont(menuFont)

    -------------------------------------------------
    -- COPY BUTTON
    -------------------------------------------------

    love.graphics.setColor(0.16, 0.16, 0.16, 0.98)

    love.graphics.rectangle(
        "fill",
        self.copyButton.x,
        self.copyButton.y,
        self.copyButton.w,
        self.copyButton.h,
        8
    )

    love.graphics.setColor(1, 1, 1)

    love.graphics.printf(
        copyText,
        self.copyButton.x,
        self.copyButton.y
        + ((buttonH - menuFont:getHeight()) / 2),
        self.copyButton.w,
        "center"
    )

    -------------------------------------------------
    -- COPY ALL BUTTON
    -------------------------------------------------

    love.graphics.setColor(0.16, 0.16, 0.16, 0.98)

    love.graphics.rectangle(
        "fill",
        self.copyAllButton.x,
        self.copyAllButton.y,
        self.copyAllButton.w,
        self.copyAllButton.h,
        8
    )

    love.graphics.setColor(1, 1, 1)

    love.graphics.printf(
        copyAllText,
        self.copyAllButton.x,
        self.copyAllButton.y
        + ((buttonH - menuFont:getHeight()) / 2),
        self.copyAllButton.w,
        "center"
    )

    -------------------------------------------------
    -- RESTORE ORIGINAL FONT
    -------------------------------------------------

    love.graphics.setFont(self.font)
end

function TextField:draw()
    -- Draw Background
    love.graphics.setColor(0.12, 0.12, 0.12)
    love.graphics.rectangle("fill", self.x, self.y, self.w, self.h, 10)
    
    if self.focused then
        love.graphics.setColor(0.4, 0.6, 1.0, 0.8)
        love.graphics.rectangle("line", self.x, self.y, self.w, self.h, 10)
    end

    --==================================================
    -- SCISSOR
    --==================================================

    love.graphics.setScissor(self.x, self.y, self.w, self.h)

    love.graphics.setFont(self.font)

    local drawX =
        self.x + self.padding + self.scrollX

    local drawY =
        self.y + self.padding + self.scrollY

    --==================================================
    -- SELECTION
    --==================================================

    if Caret.hasSelection(self) then

        local s =
            math.min(self.selStart, self.selEnd)

        local e =
            math.max(self.selStart, self.selEnd)

        love.graphics.setColor(0.2, 0.5, 1.0, 0.45)

        -------------------------------------------------
        -- HORIZONTAL
        -------------------------------------------------

        if self.mode == "horizontal" then

            local before =
                Caret.sub(self.text, 1, s - 1)

            local selected =
                Caret.sub(self.text, s, e - 1)

            local startX =
                self.font:getWidth(before)

            local width =
                self.font:getWidth(selected)

            love.graphics.rectangle(
                "fill",
                drawX + startX,
                drawY,
                width,
                self.font:getHeight()
            )

        -------------------------------------------------
        -- VERTICAL
        -------------------------------------------------

        else

            local widthLimit =
                self.w - (self.padding * 2)

            local _, wrappedLines =
                self.font:getWrap(
                    self.text,
                    widthLimit
                )

            local globalIndex = 1
            local lineHeight =
                self.font:getHeight()

            for lineIndex, lineText in ipairs(wrappedLines) do

                local lineStart =
                    globalIndex

                local lineLen =
                    utf8.len(lineText) or 0

                -- FIX: last char is inclusive index
                local lineEnd =
                    lineStart + lineLen - 1

                -- selection is [s, e-1] internally
                local selStart = math.min(self.selStart, self.selEnd)
                local selEndExclusive = math.max(self.selStart, self.selEnd)

                local selStartIdx = selStart
                local selEndIdx = selEndExclusive - 1

                -- FIX: proper overlap test for inclusive ranges
                local intersects =
                    selStartIdx <= lineEnd and selEndIdx >= lineStart

                if intersects and lineLen > 0 then

                    local localSelStart =
                        math.max(selStartIdx, lineStart)

                    local localSelEnd =
                        math.min(selEndIdx, lineEnd)

                    local startOffset =
                        localSelStart - lineStart

                    local endOffset =
                        localSelEnd - lineStart + 1

                    local before =
                        Caret.sub(
                            lineText,
                            1,
                            startOffset
                        )

                    local selected =
                        Caret.sub(
                            lineText,
                            startOffset + 1,
                            endOffset
                        )

                    local startX =
                        self.font:getWidth(before)

                    local width =
                        self.font:getWidth(selected)

                    local y =
                        drawY
                        + ((lineIndex - 1)
                        * lineHeight)

                    love.graphics.rectangle(
                        "fill",
                        drawX + startX,
                        y,
                        width,
                        lineHeight
                    )
                end

                globalIndex = lineEnd + 1
            end
        end
    end

    --==================================================
    -- TEXT
    --==================================================

    love.graphics.setColor(1, 1, 1)

    if self.mode == "horizontal" then

        love.graphics.print(
            self.text,
            drawX,
            drawY
        )

    else

        love.graphics.printf(
            self.text,
            drawX,
            drawY,
            self.w - self.padding * 2
        )
    end

    --==================================================
    -- CARET
    --==================================================

    if self.focused and self.visible and self.editMode == "editable" then

        local cx, cy =
            self:getCaretVisualPos()

        love.graphics.setColor(0.4, 0.6, 1.0)

        love.graphics.line(
            drawX + cx,
            drawY + cy,
            drawX + cx,
            drawY + cy + self.font:getHeight()
        )
    end

    -------------------------------------------------
    -- SELECTION MENU
    -------------------------------------------------

    if self.selectionMode == "full" then
        self:drawSelectionMenu(drawX, drawY)
    end

    --==================================================
    -- CLEAR SCISSOR
    --==================================================

    love.graphics.setScissor()

    -- Enhanced Scrollbar (Vertical mode only)
    if self.mode == "vertical" then
        local _, totalH = self:getTextDimensions()
        local visibleH = self.h - (self.padding * 2)
        
        if totalH > visibleH then
            -- 1. Calculate Dimensions
            local ratio = visibleH / totalH
            local barH = math.max(20, visibleH * ratio) -- Ensure thumb isn't too tiny
            local barY = self.y + self.padding + (-self.scrollY / (totalH - visibleH)) * (visibleH - barH)
            
            -- 2. Determine State (Wider when dragging or hovering)
            local mx, my = love.mouse.getPosition()
            local isHovering = mx >= (self.x + self.w - 15) and mx <= (self.x + self.w) and
                               my >= self.y and my <= (self.y + self.h)
            
            local isActive = self.isDraggingScroll or isHovering
            local barWidth = isActive and 10 or 4  -- Thin to Thick transition
            local trackX = self.x + self.w - (barWidth + 4)
            
            -- 3. Draw Track (The background of the scrollbar)
            love.graphics.setColor(1, 1, 1, 0.05) -- Very subtle track
            love.graphics.rectangle("fill", trackX, self.y + self.padding, barWidth, visibleH, 5)
            
            -- 4. Draw Thumb
            local alpha = self.isDraggingScroll and 0.8 or (isHovering and 0.5 or 0.3)
            love.graphics.setColor(1, 1, 1, alpha)
            love.graphics.rectangle("fill", trackX, barY, barWidth, barH, 5)
        end
    end

end

return TextField