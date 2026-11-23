
caret.lua


local utf8 = require("utf8")
local Caret = {}

function Caret.sub(str, i, j)
    if not str or str == "" then return "" end
    local a = utf8.offset(str, i)
    if not a then return "" end
    local b
    if j then
        b = utf8.offset(str, j + 1)
        if b then b = b - 1 end
    end
    return str:sub(a, b or -1)
end

function Caret.insert(data, char)
    local before = Caret.sub(data.text, 1, data.pos - 1)
    local after = Caret.sub(data.text, data.pos)
    data.text = before .. char .. after
    data.pos = data.pos + (utf8.len(char) or 1)
end

function Caret.backspace(data)
    if data.pos <= 1 then return end
    local before = Caret.sub(data.text, 1, data.pos - 2)
    local after = Caret.sub(data.text, data.pos)
    data.text = before .. after
    data.pos = data.pos - 1
end

return Caret





keyboard.lua

-- keyboard.lua (Gboard-Precise Layout + Symbols Fix)
local utf8 = require("utf8")

local Keyboard = {}

-- state
local textBuffer = ""
local active = false
local callback = nil
local mode = "alpha" 
local shiftState = 0 
local pressedKey = nil
local pendingKey = nil
local isHolding = false
local repeatTimer = 0
local repeatDelay = 0.4
local repeatInterval = 0.05
local lastShiftTap = 0        
local doubleTapWindow = 0.4   

-- Gboard Precise Layouts (Added missing Symbols layout)
local layouts = {
    alpha = {
        {"q","w","e","r","t","y","u","i","o","p"},
        {"a","s","d","f","g","h","j","k","l"},
        {"shift","z","x","c","v","b","n","m","backspace"},
        {"?123",",","space",".","enter"}
    },
    numeric = {
        {"1","2","3","4","5","6","7","8","9","0"},
        {"@","#","$","&","-","+","(",")","/","*"},
        {"alt","!","\"","'","?",":",";","backspace"},
        {"ABC",",","space",".","enter"}
    },
    symbols = {
        {"~","`","|","•","π","÷","×","¶","£","¢"},
        {"€","¥","^","°","=","{","}","\\","%","©"},
        {"alt","®","™","[","]","backspace"},
        {"ABC",",","space",".","enter"}
    }
}

Keyboard.keys = {}

function Keyboard.rebuild()
    if not active then return end

    local sw, sh = love.graphics.getWidth(), love.graphics.getHeight()

    local currentLayout = layouts[mode]
    if not currentLayout then
        mode = "alpha"
        currentLayout = layouts.alpha
    end

    ---------------------------------------------------
    -- KEYBOARD AREA
    ---------------------------------------------------

    local keyboardH = sh * 0.40
    local keyboardW = sw

    local keyboardX = 0
    local keyboardY = sh - keyboardH

    ---------------------------------------------------
    -- BUFFER
    ---------------------------------------------------

    local bufferX = sw * 0.015
    local bufferY = keyboardH * 0.03

    local innerX = keyboardX + bufferX
    local innerY = keyboardY + bufferY

    local innerW = keyboardW - (bufferX * 2)
    local innerH = keyboardH - (bufferY * 2)

    ---------------------------------------------------
    -- GAPS
    ---------------------------------------------------

    local keyGap = math.max(3, sw * 0.005)

    ---------------------------------------------------
    -- ROW / COLUMN CONFIG
    ---------------------------------------------------

    local rowConfigs = {
        alpha = {10, 9, 7},
        numeric = {10, 10, 7},
        symbols = {10, 10, 7}
    }

    local columns = rowConfigs[mode]

    ---------------------------------------------------
    -- ROW HEIGHT
    ---------------------------------------------------

    local rowGap = keyGap
    local rowCount = 4

    local keyH =
        (innerH - (rowGap * (rowCount - 1)))
        / rowCount

    Keyboard.keys = {}

    ---------------------------------------------------
    -- BUILD ROWS
    ---------------------------------------------------

    for r, row in ipairs(currentLayout) do

        local rowY =
            math.floor(
                innerY + ((r - 1) * (keyH + rowGap))
            )

        ---------------------------------------------------
        -- ROWS 1-3
        ---------------------------------------------------

        if r <= 3 then

            local colCount = columns[r]

            local usableW =
                innerW - (keyGap * (colCount - 1))

            local keyW =
                usableW / colCount

            local totalRowW =
                (keyW * colCount)
                + (keyGap * (colCount - 1))

            local baseCols = columns[1] -- always 10 from alpha layout

            local baseW =
                (innerW - (keyGap * (baseCols - 1)))

            local baseKeyW =
                baseW / baseCols

            local rowCols = colCount

            local rowW =
                (keyW * rowCols)
                + (keyGap * (rowCols - 1))

            -- align row 2 (and others) to same center reference as row 1
            local baseRowW =
                (baseKeyW * baseCols)
                + (keyGap * (baseCols - 1))

            local startX =
                innerX + ((baseRowW - rowW) / 2)

            ---------------------------------------------------
            -- ROW 3 SPECIAL KEYS
            ---------------------------------------------------

            if r == 3 then

                local sideGrow = keyW * 0.5

                local normalKeys = #row - 2

                local sideW = keyW + sideGrow

                local remainingW =
                    innerW
                    - (sideW * 2)
                    - (keyGap * (#row - 1))

                local normalW =
                    remainingW / normalKeys

                local x = startX

                for _, key in ipairs(row) do

                    local w = normalW

                    if key == "shift"
                    or key == "backspace"
                    or key == "alt" then
                        w = sideW
                    end

                    table.insert(Keyboard.keys, {
                        key = key,
                        x = math.floor(x),
                        y = rowY,
                        w = math.floor(w),
                        h = math.floor(keyH)
                    })

                    x = x + w + keyGap
                end

            else

                ---------------------------------------------------
                -- NORMAL ROWS
                ---------------------------------------------------

                local x = startX

                for _, key in ipairs(row) do

                    table.insert(Keyboard.keys, {
                        key = key,
                        x = math.floor(x),
                        y = rowY,
                        w = math.floor(keyW),
                        h = math.floor(keyH)
                    })

                    x = x + keyW + keyGap
                end
            end

        ---------------------------------------------------
        -- BOTTOM ROW
        ---------------------------------------------------

        else

            local keyGap = math.max(3, sw * 0.005)

            local keyWidths = {
                ["space"] = innerW * 0.42,
                ["enter"] = innerW * 0.14,
                ["?123"] = innerW * 0.14,
                ["ABC"] = innerW * 0.14,
                [","] = innerW * 0.08,
                ["."] = innerW * 0.08
            }

            -- keys that are allowed to expand
            local flexKeys = {
                ["enter"] = true,
                ["?123"] = true,
                ["ABC"] = true
            }

            local baseW = 0
            local flexCount = 0

            for _, key in ipairs(row) do
                local w = keyWidths[key] or (innerW * 0.1)
                baseW = baseW + w

                if flexKeys[key] then
                    flexCount = flexCount + 1
                end
            end

            local totalGapW = keyGap * (#row - 1)
            local leftover = innerW - baseW - totalGapW

            local extraPerFlex = 0
            if flexCount > 0 and leftover > 0 then
                extraPerFlex = leftover / flexCount
            end

            local x = innerX

            for _, key in ipairs(row) do

                local w = keyWidths[key] or (innerW * 0.1)

                -- distribute leftover evenly across ALL function keys
                if flexKeys[key] and leftover > 0 then
                    w = w + extraPerFlex
                end

                table.insert(Keyboard.keys, {
                    key = key,
                    x = math.floor(x),
                    y = rowY,
                    w = math.floor(w),
                    h = math.floor(keyH)
                })

                x = x + w + keyGap
            end
        end
    end

    Keyboard.x = keyboardX
    Keyboard.y = keyboardY
    Keyboard.w = keyboardW
    Keyboard.h = keyboardH
end

function Keyboard.update(dt)
    if not active or not love.mouse.isDown(1) or not pendingKey then
        pressedKey = nil
        return 
    end

    if pendingKey.key == "backspace" then
        repeatTimer = repeatTimer + dt
        if repeatTimer >= repeatDelay then
            if (repeatTimer - repeatDelay) >= repeatInterval then
                if callback then callback("backspace") end                
                repeatTimer = repeatDelay 
            end
        end
    end
end

function Keyboard.draw()
    if not active then return end
    Keyboard.rebuild()

    love.graphics.setColor(0.1, 0.1, 0.1, 1)
    love.graphics.rectangle("fill", 0, Keyboard.y, love.graphics.getWidth(), love.graphics.getHeight() - Keyboard.y)

    local font = love.graphics.getFont()
    local fontH = font:getHeight()

    for _, k in ipairs(Keyboard.keys) do
        local isFunc = (k.key == "shift" or k.key == "backspace" or k.key == "?123" or k.key == "ABC" or k.key == "enter" or k.key == "alt")
        local isPressed = (pressedKey and pressedKey.key == k.key)
        
        -- Darken Effect
        if isPressed then
            love.graphics.setColor(0.05, 0.05, 0.05, 1) 
        elseif k.key == "shift" and shiftState > 0 then
            love.graphics.setColor(0.2, 0.3, 0.5, 1) 
        else
            love.graphics.setColor(isFunc and 0.15 or 0.22, isFunc and 0.15 or 0.22, isFunc and 0.15 or 0.22, 1)
        end
        
        love.graphics.rectangle("fill", k.x, k.y, k.w, k.h, 6)

        love.graphics.setColor(1, 1, 1, 1)
        local label = k.key
        if mode == "alpha" and not isFunc then
            label = (shiftState > 0) and label:upper() or label:lower()
        end

        -- Gboard Pop-up for characters
        if isPressed and not isFunc and k.key ~= "space" then
            local popW, popH = k.w * 1.3, k.h * 1.6
            local popX, popY = k.x - (popW - k.w) / 2, k.y - popH - 2
            
            love.graphics.setColor(0.25, 0.25, 0.25, 1)
            love.graphics.rectangle("fill", popX, popY, popW, popH, 10)
            
            love.graphics.setColor(1, 1, 1, 1)
            local bigFont = love.graphics.newFont(k.h * 0.7) 
            love.graphics.setFont(bigFont)
            love.graphics.printf(label, popX, popY + (popH - bigFont:getHeight())/2, popW, "center")
            love.graphics.setFont(font) 
        end

        -- Labels & Icons
        love.graphics.setColor(1, 1, 1, 1)
        local textY = k.y + (k.h - fontH) / 2
        
        if k.key == "shift" then
            local cx, cy = k.x + k.w/2, k.y + k.h/2
            
            -- State 0: Hollow Arrow
            -- State 1: Filled Arrow
            -- State 2: Filled Arrow + Bar
            local drawMode = (shiftState > 0) and "fill" or "line"
            
            love.graphics.polygon(drawMode, cx, cy-8, cx-7, cy+2, cx-3, cy+2, cx-3, cy+7, cx+3, cy+7, cx+3, cy+2, cx+7, cy+2)
            
            -- The "Caps Lock" bar
            if shiftState == 2 then 
                love.graphics.rectangle("fill", cx-6, cy+10, 12, 2) 
            end
            
        elseif k.key == "backspace" then
            local cx, cy = k.x + k.w/2, k.y + k.h/2
            
            -- Draw the outer "tag" shape
            love.graphics.line(cx-10, cy, cx-2, cy-7, cx+10, cy-7, cx+10, cy+7, cx-2, cy+7, cx-10, cy)
            
            -- Draw the "X" icon inside
            local xSize = 3
            local offsetX = 3 -- Shift the X slightly right to center it in the rectangular part
            
            -- Line 1: Top-left to bottom-right
            love.graphics.line(cx + offsetX - xSize, cy - xSize, cx + offsetX + xSize, cy + xSize)
            -- Line 2: Bottom-left to top-right
            love.graphics.line(cx + offsetX - xSize, cy + xSize, cx + offsetX + xSize, cy - xSize)
        elseif k.key == "enter" then 
            love.graphics.printf("DONE", k.x, textY, k.w, "center")
        elseif k.key == "alt" then 
            love.graphics.printf((mode == "numeric") and "=\\<" or "?123", k.x, textY, k.w, "center")
        elseif k.key ~= "space" then
            love.graphics.printf(label, k.x, textY, k.w, "center")
        end
    end
end

function Keyboard.mousepressed(x, y, button)
    if not active or button ~= 1 then return end

    for _, k in ipairs(Keyboard.keys) do
        if x >= k.x and x <= k.x + k.w and y >= k.y and y <= k.y + k.h then
            pressedKey = k
            pendingKey = k
            isHolding = true
            repeatTimer = 0

            if k.key == "shift" then
                local currentTime = love.timer.getTime()
                
                if (currentTime - lastShiftTap) < doubleTapWindow then
                    -- Double tap detected: Go to Caps Lock
                    shiftState = 2
                    lastShiftTap = currentTime -- Keep timer alive to allow toggling OFF
                elseif shiftState == 0 then
                    -- Off -> Single
                    shiftState = 1
                    lastShiftTap = currentTime
                else
                    -- Single/Caps -> Off
                    shiftState = 0
                    -- CRITICAL: Reset the timer to 0 so the NEXT click 
                    -- cannot be considered a double-tap.
                    lastShiftTap = 0 
                end
            end
            return
        end
    end
end

function Keyboard.mousereleased(x, y, button)
    if not active or button ~= 1 then return end
    if not pendingKey then 
        
        return 
    end

    local k = pendingKey
    
    repeatTimer = 0
    pendingKey = nil
    pressedKey = nil
    isHolding = false

    -- BACKSPACE
    if k.key == "backspace" then
        if callback then callback("backspace") end

    -- SPACE
    elseif k.key == "space" then
        if callback then callback("insert", " ") end

    -- ENTER
    elseif k.key == "enter" then
        if callback then callback("enter") end

    -- MODE SWITCH
    elseif k.key == "?123" then mode = "numeric"
    elseif k.key == "ABC" then mode = "alpha"
    elseif k.key == "alt" then mode = (mode == "numeric") and "symbols" or "numeric"

    -- SHIFT (only handle here, not in mousepressed)
    elseif k.key == "shift" then
        -- Do nothing here! We handled the state in mousepressed.

    -- CHARACTER
    else
        local char = (shiftState > 0) and k.key:upper() or k.key:lower()
        if callback then callback("insert", char) end
        
        -- ONLY reset if we were in "Single Shift" mode
        if shiftState == 1 then 
            shiftState = 0 
        end
    end

end

function Keyboard.setActive(s, c)

    active = s
    callback = c

    mode = "alpha"
    shiftState = 0
    pressedKey = nil

    if active then
        Keyboard.rebuild()
    end
end

function Keyboard.isActive()
    return active 
end

function Keyboard.setText(t)
    textBuffer = t or "" 
end

function Keyboard.getText() 
    return textBuffer 
end

function Keyboard.contains(x, y)


    if not active then
        return false
    end

    if not Keyboard.x then
        Keyboard.rebuild()
    end

    return
        x >= Keyboard.x and
        x <= Keyboard.x + Keyboard.w and
        y >= Keyboard.y and
        y <= Keyboard.y + Keyboard.h
end

return Keyboard




textfield.lua

local Caret = require("caret")
local utf8 = require("utf8")
local Keyboard = require("keyboard")

local TextField = {}
TextField.__index = TextField

-- Static variable to keep track of who is currently typing
TextField.activeField = nil

function TextField.new(x, y, w, h, font)
    local self = setmetatable({}, TextField)
    self.x, self.y, self.w, self.h = x, y, w, h
    self.font = font
    
    -- Internal State
    self.text = ""
    self.pos = 1
    self.blink = 0
    self.visible = true
    self.focused = false

    return self
end

function TextField:update(dt)
    if not self.focused then return end
    self.blink = self.blink + dt
    if self.blink >= 0.5 then
        self.blink = 0
        self.visible = not self.visible
    end
end

function TextField:handleInput(action, value)
    if not self.focused then return end
    if action == "insert" then
        Caret.insert(self, value)
    elseif action == "backspace" then
        Caret.backspace(self)
    end
    self.visible = true
    self.blink = 0
end

function TextField:mousepressed(mx, my)
    local insideField = mx >= self.x and mx <= self.x + self.w and 
                        my >= self.y and my <= self.y + self.h
    
    local insideKeyboard = false
    if Keyboard.isActive() then
        insideKeyboard = mx >= Keyboard.x and mx <= Keyboard.x + Keyboard.w and
                         my >= Keyboard.y and my <= Keyboard.y + Keyboard.h
    end

    if insideField then
        -- 1. CLICKED INSIDE FIELD: Focus up and show keyboard
        self.focused = true
        TextField.activeField = self
        
        -- Pop the keyboard up
        Keyboard.setActive(true, function(action, value)
            if TextField.activeField then
                TextField.activeField:handleInput(action, value)
            end
        end)

        -- Position the caret (your existing logic)
        local relativeX = mx - (self.x + 10)
        local cumulativeW = 0
        local length = utf8.len(self.text) or 0
        self.pos = 1
        for i = 1, length do
            local char = Caret.sub(self.text, i, i)
            local charW = self.font:getWidth(char)
            if relativeX < (cumulativeW + charW / 2) then break end
            cumulativeW = cumulativeW + charW
            self.pos = i + 1
        end
        return true

    elseif insideKeyboard then
        -- 2. CLICKED KEYBOARD: Don't lose focus, don't close keyboard
        return true 

    else
        -- 3. CLICKED OUTSIDE BOTH: Shut everything down
        self.focused = false
        if TextField.activeField == self then
            TextField.activeField = nil
            Keyboard.setActive(false) -- This hides the keyboard
        end
        return false
    end
end

function TextField:draw()
    -- Draw Box
    love.graphics.setColor(0.15, 0.15, 0.15)
    love.graphics.rectangle("fill", self.x, self.y, self.w, self.h, 10)
    if self.focused then
        love.graphics.setColor(0.3, 0.5, 0.8, 0.5) -- Highlight border
        love.graphics.rectangle("line", self.x, self.y, self.w, self.h, 10)
    end

    -- Draw Text
    love.graphics.setFont(self.font)
    local tx, ty = self.x + 10, self.y + (self.h - self.font:getHeight()) / 2
    love.graphics.setColor(1, 1, 1)
    love.graphics.print(self.text, tx, ty)

    -- Draw Caret
    if self.focused and self.visible then
        local before = Caret.sub(self.text, 1, self.pos - 1)
        local offset = self.font:getWidth(before)
        love.graphics.line(tx + offset, ty, tx + offset, ty + self.font:getHeight())
    end
end

return TextField





main.lua(partial)



function love.mousepressed(x, y, button)
    
    if button ~= 1 then return end
    
    if isInteractionBlocked(x, y) then
        return
    end

    -- ⭐ Selection toast priority check (MUST BE FIRST)
    if selectionActionToast then

        local t = selectionActionToast

        local insideToast =
            x >= t.x and x <= t.x + t.w and
            y >= t.y and y <= t.y + t.h

        if insideToast then
            local box = resolveHitbox(x, y)
            if box and box.onClick then
                box.onClick()
            end

            return
        end

        -- Click outside → dismiss toast
        selectionActionToast = nil
    end

    if selectionStart and selectionEnd then
        if selectionActionToast then
            
            selectionActionToast = nil
            selectionStart = nil
            selectionEnd = nil
            selectionBox = nil
        end
    end

    if currentTab == "interpreter" then

        -- =========================
        -- KEYBOARD INPUT HANDLING
        -- =========================

        local insideInput =
            x >= inputBox.x and
            x <= inputBox.x + inputBox.w - SCROLLBAR_TRACK_W and
            y >= inputBox.y and
            y <= inputBox.y + inputBox.h

        if insideInput then

            if isAndroidPlatform() then
                inputActive = true

                showFadeMessage("INPUT CLICKED - setting keyboard active")

                Keyboard.setText(rawText)

                Keyboard.setActive(true, function(newText)

                    showFadeMessage("CALLBACK FIRED with: " .. newText)

                    rawText = newText

                    updateTextWrapping()

                end)
            end

        else

            if isAndroidPlatform() then
                if Keyboard.isActive() and not Keyboard.contains(x, y) then

                    inputActive = false
                    Keyboard.setActive(false)

                end
            end
        end

        if isAndroidPlatform() then
            for _, f in ipairs(textFields) do
                f:mousepressed(x, y)
            end
            -- Forward presses to keyboard
            if Keyboard.isActive() and Keyboard.contains(x, y) then

                showFadeMessage("KEY PRESSED at x=" .. x .. " y=" .. y)

                Keyboard.mousepressed(x, y, button)

                return
            end
        end

        local box =
            ((x >= inputBox.x and x <= inputBox.x + inputBox.w - SCROLLBAR_TRACK_W and
              y >= inputBox.y and y <= inputBox.y + inputBox.h) and inputBox)
            or
            ((x >= outputBox.x and x <= outputBox.x + outputBox.w - SCROLLBAR_TRACK_W and
              y >= outputBox.y and y <= outputBox.y + outputBox.h) and outputBox)

        if box then

            selectionBox = box
              

            local lines =
                (box == inputBox) and wrappedInput or wrappedOutput

            local pos =
                getTextIndexFromPosition(box, lines, x, y)

            if pos then

                local globalIndex = 0

                for i = 1, pos.line - 1 do
                    globalIndex = globalIndex + utf8.len(lines[i] or "")
                end

                globalIndex = globalIndex + pos.char 

                caretIndex = globalIndex
                updateTextInputCaretViewport()

                selectionBox = box
                selectionPending = true
                selectionAnchor = pos
                selectionDragStarted = false
                selectionStart = nil
                selectionEnd = nil

            end

        end
    end
    
    -- Toast blocks everything except its scroller and close btn
    if toastPopup then
        local contentH = #toastPopup.lines * lineH + padding * 2
        local viewH = toastPopup.h - 95
        local scrollTrackX = toastPopup.x + toastPopup.w - SCROLLBAR_TRACK_W
        local scrollTrackY = toastPopup.y + 45
        local contentAreaX = toastPopup.x
        local contentAreaY = toastPopup.y + 45
        local contentAreaW = toastPopup.w - SCROLLBAR_TRACK_W
        local contentAreaH = viewH

        if contentH > viewH then
            if x >= scrollTrackX and x <= scrollTrackX + SCROLLBAR_TRACK_W and
            y >= scrollTrackY and y <= scrollTrackY + viewH then
                draggingBox = { isToast = true, box = toastPopup, lines = toastPopup.lines }
                return
            end
        end

        -- Selection toast action buttons
        if toastSelectionActionToast then
            local t = toastSelectionActionToast
            local insideToast = x >= t.x and x <= t.x + t.w and y >= t.y and y <= t.y + t.h
            if insideToast then
                local box = resolveHitbox(x, y)
                if box and box.onClick then box.onClick() end
                return
            end
            toastSelectionActionToast = nil
            toastSelectionStart = nil
            toastSelectionEnd = nil
        end

        -- Start selection drag inside toast content area
        if x >= contentAreaX and x <= contentAreaX + contentAreaW and
        y >= contentAreaY and y <= contentAreaY + contentAreaH then

            toastSelectionStart = nil
            toastSelectionEnd = nil
            selectionPending = false
            selectionAnchor = nil
            selectionDragStarted = false

            -- Build a fake box proxy so we can reuse getTextIndexFromPosition
            toastSelectionBox = {
                x = toastPopup.x,
                y = toastPopup.y + 30,
                w = toastPopup.w - SCROLLBAR_TRACK_W,
                h = viewH,
                scrollY = toastPopup.scrollY
            }

            local pos = getTextIndexFromPosition(toastSelectionBox, toastPopup.lines, x, y)
            if pos then
                toastSelectionStart = pos
                toastSelectionEnd = pos
                selectionPending = true
                selectionAnchor = pos
                selectionDragStarted = false
            end
            return
        end

        local box = resolveHitbox(x, y)
        if box and box.onClick then box.onClick() end
        return
    end

    -- Saves scroller
    if currentTab == "saves" then
        local info = getSavesScrollerInfo()
        local trackX =
        sw - SAVE_LIST_RIGHT_MARGIN * SAVE_SCROLLBAR_CENTER_X_RATIO
        if info then
            if math.abs(x - trackX) <= SCROLLBAR_TRACK_W and
                y >= info.trackY and y <= info.trackY + info.trackH then
                draggingSavesScroll = true
                return
            end
        end
    end

    local box = resolveHitbox(x, y)
    if box and box.onClick then box.onClick() end

end

function love.mousemoved(x, y, dx, dy, istouch)

    local dt = love.timer.getDelta()
    if draggingBox and draggingBox.isToast then
        local box = draggingBox.box
        local lines = draggingBox.lines
        local contentH = #lines * lineH + padding * 2
        local viewH = box.h - 95
        local maxScroll = contentH - viewH
        local scrollPercent = math.max(0, math.min(viewH, y - (box.y + 45))) / viewH
        box.scrollY = -scrollPercent * maxScroll
        return
    end
    if draggingBox and not draggingBox.isToast then
        local box = draggingBox.box
        local lines = draggingBox.lines
        local contentH = #lines * lineH + padding * 2
        local maxScroll = contentH - box.h
        local scrollPercent = math.max(0, math.min(box.h, y - box.y)) / box.h
        box.scrollY = -scrollPercent * maxScroll
        return
    end
    if draggingSavesScroll then
        local info = getSavesScrollerInfo()
        if info then
            local scrollPercent = math.max(0, math.min(info.trackH, y - info.trackY)) / info.trackH
            savesScrollY = -scrollPercent * info.maxScroll
        end
        return
    end

    -- Toast text selection drag
    if toastPopup and toastSelectionBox and love.mouse.isDown(1) and selectionPending and selectionAnchor then
        toastSelectionBox.scrollY = toastPopup.scrollY
        toastSelectionBox.y = toastPopup.y + 30

        -- ===== AUTO SCROLL LOGIC =====
        local autoScrollMargin = 30
        local scrollVelocity = 500
        local viewH = toastPopup.h - 95
        local contentH = #toastPopup.lines * lineH + padding * 2
        local maxScroll = math.max(0, contentH - viewH)

        if y < toastSelectionBox.y + autoScrollMargin then
            toastPopup.scrollY = toastPopup.scrollY + scrollVelocity * dt
            toastPopup.scrollY = math.min(0, toastPopup.scrollY)
            toastPopup.scrollY = math.max(-maxScroll, toastPopup.scrollY)
        elseif y > toastSelectionBox.y + viewH - autoScrollMargin then
            toastPopup.scrollY = toastPopup.scrollY - scrollVelocity * dt
            toastPopup.scrollY = math.max(-maxScroll, toastPopup.scrollY)
        end

        -- sync proxy after scroll update
        toastSelectionBox.scrollY = toastPopup.scrollY

        local pos = getTextIndexFromPosition(toastSelectionBox, toastPopup.lines, x, y)
        if pos then
            if not selectionDragStarted then
                local moved = pos.line ~= selectionAnchor.line or pos.char ~= selectionAnchor.char
                if moved then
                    selectionDragStarted = true
                    toastSelectionStart = selectionAnchor
                    toastSelectionEnd = pos
                end
            else
                toastSelectionEnd = pos
            end
        end

        if toastSelectionStart and toastSelectionEnd then
            openToastSelectionActionToast(toastSelectionEnd.line, toastSelectionEnd.char)
        end
        return
    end

    if selectionBox and love.mouse.isDown(1) and selectionPending and selectionAnchor then

        local box = selectionBox
        local lines =
            (box == inputBox) and wrappedInput or wrappedOutput

        if not lines then return end

        -- ===== AUTO SCROLL LOGIC =====
        local autoScrollMargin = 30

        -- ⭐ Editor-like scroll velocity (pixels per second)
        local scrollVelocity = 500

        local contentH = #lines * lineH + padding * 2
        local maxScroll = math.max(0, contentH - box.h)

        -- Scroll UP
        if my < box.y + autoScrollMargin then

            box.scrollY = box.scrollY + scrollVelocity * dt
            box.scrollY = math.min(0, box.scrollY)
            box.scrollY = math.max(-maxScroll, box.scrollY)

        -- Scroll DOWN
        elseif my > box.y + box.h - autoScrollMargin then

            box.scrollY = box.scrollY - scrollVelocity * dt
            box.scrollY = math.max(-maxScroll, box.scrollY)

        end
        local pos = getTextIndexFromPosition(box, lines, x, y)

        if pos then

            if not selectionDragStarted then

                local moved =
                    pos.line ~= selectionAnchor.line or
                    pos.char ~= selectionAnchor.char

                if moved then
                    selectionDragStarted = true
                    selectionStart = selectionAnchor
                    selectionEnd = pos
                end

            else
                selectionEnd = pos
            end
        end
    end

    if selectionStart and selectionEnd then
        local box = selectionBox
        openSelectionToast(box, selectionEnd.line, selectionEnd.char)
    end
end

function love.mousereleased(x, y, button)
    draggingBox = nil
    draggingSavesScroll = false

    moveCaretToSelectionEnd()
    selectionDragStarted = false
    if toastPopup then
        selectionPending = false
        selectionDragStarted = false
    end

    if isAndroidPlatform() then
        if Keyboard.isActive() then

            showFadeMessage("MOUSE RELEASED")

            Keyboard.mousereleased(x, y, button)

        end
    end

end

function drawBox(ctx, box, title, lines, textColor)

    local mx, my = ctx.mx, ctx.my
    local padding = ctx.padding
    local lineH = ctx.lineH

    local inputBox = ctx.inputBox
    local outputBox = ctx.outputBox

    local font = ctx.font

    local selectionStart = ctx.selectionStart
    local selectionEnd = ctx.selectionEnd

    local wrappedInput = ctx.wrappedInput
    local wrappedOutput = ctx.wrappedOutput

    local hoverBox =
        mx >= box.x and mx <= box.x + box.w and
        my >= box.y and my <= box.y + box.h

    local baseBorder = {0.04, 0.04, 0.05}

    if box == inputBox and inputBox.active then
        baseBorder = {0.12, 0.12, 0.18}
    end

    if hoverBox then
        baseBorder[1] = baseBorder[1] + 0.1
        baseBorder[2] = baseBorder[2] + 0.1
        baseBorder[3] = baseBorder[3] + 0.1
    end

    love.graphics.setColor(baseBorder)
    love.graphics.rectangle("fill", box.x, box.y, box.w, box.h, 6)

    love.graphics.setColor(
        (box == inputBox and inputBox.active)
        and {0.3, 0.6, 1}
        or {0.2, 0.2, 0.2}
    )
    love.graphics.rectangle("line", box.x, box.y, box.w, box.h, 6)

    love.graphics.setScissor(box.x, box.y, box.w, box.h)

    -- selection highlight (safe minimal version)
    if selectionStart and selectionEnd and ctx.selectionBox == box then
        local s, e = normalizeSelection(selectionStart, selectionEnd)
        local fontObj = love.graphics.getFont()

        for line = s.line, e.line do
            local lineText = lines[line]
            if lineText then

                local startChar = 1
                local endChar = utf8.len(lineText) or 0

                if line == s.line then startChar = s.char end
                if line == e.line then endChar = e.char end

                local prefix = utf8_sub(lineText, 1, startChar - 1)
                local sel = utf8_sub(lineText, startChar, endChar)

                local px = fontObj:getWidth(prefix)
                local selW = fontObj:getWidth(sel)

                local y = box.y + padding + (line - 1) * lineH

                love.graphics.setColor(0.3, 0.5, 1, 0.4)
                love.graphics.rectangle(
                    "fill",
                    box.x + padding + px,
                    y,
                    selW,
                    lineH
                )
            end
        end
    end

    love.graphics.setScissor()

    -- title
    love.graphics.setColor(1, 1, 1)
    love.graphics.setFont(ctx.titleFont or font)
    love.graphics.print(title, box.x, box.y - 22)
    love.graphics.setFont(font)

    -- text rendering
    love.graphics.setScissor(box.x, box.y, box.w, box.h)
    love.graphics.setColor(textColor)

    for i, line in ipairs(lines) do
        local drawY = box.y + padding + (i - 1) * lineH
        if drawY + lineH > box.y and drawY < box.y + box.h then
            love.graphics.print(line, box.x + padding, drawY)
        end
    end

    love.graphics.setScissor()
end

function love.draw()

    clearHitboxes()

    love.graphics.clear(0.07, 0.07, 0.09)
    local tabH = 40
    local tabY = sh - tabH
    languageText = languages[currentLang]

    local ctx = {
        mx = mx,
        my = my,

        padding = padding,
        lineH = lineH,

        font = font,
        titleFont = titleFont,

        inputBox = inputBox,
        outputBox = outputBox,

        selectionStart = selectionStart,
        selectionEnd = selectionEnd,

        wrappedInput = wrappedInput,
        wrappedOutput = wrappedOutput,

        selectionBox = selectionBox
    }

    
    -- ===================== INTERPRETER TAB =====================
    if currentTab == "interpreter" then    

        local inputText = (mode == "interpret") and "INPUT (encoded)" or "INPUT (normal)"
        local inputTextWidth = love.graphics.getFont():getWidth(inputText)
        local inputTextHeight = love.graphics.getFont():getHeight()
        local outputText = (mode == "interpret") and "OUTPUT (normal)" or "OUTPUT (encoded)"
        drawBox(ctx, inputBox, inputText, wrappedInput, {0.9, 0.9, 0.9})
        drawBox(ctx, outputBox, outputText, wrappedOutput, {0.4, 0.9, 0.4})

        -- Adjusted Hitboxes: Width reduced by SCROLLBAR_TRACK_W to ignore scroller area
        addHitbox(inputBox.x, inputBox.y, inputBox.w - SCROLLBAR_TRACK_W, inputBox.h, function()
            inputBox.active = true; outputBox.active = false
            if isAndroidPlatform() then 
                love.keyboard.setTextInput(true) 
                showFadeMessage("BOX ACTIVE: " .. tostring(inputBox.active) .. " TI: " .. tostring(love.keyboard.hasTextInput()))
            end
        end, 1)

        addHitbox(outputBox.x, outputBox.y, outputBox.w - SCROLLBAR_TRACK_W, outputBox.h, function()
            outputBox.active = true; inputBox.active = false
        end, 1)

        -- Input box scrollbar hitbox
        local function registerBoxScrollerHitbox(box, lines)
            local contentH = #lines * lineH + padding * 2

            if contentH > box.h then
                local trackX = box.x + box.w - SCROLLBAR_TRACK_W
                local trackY = box.y
                local trackW = SCROLLBAR_TRACK_W
                local trackH = box.h

                addHitbox(trackX, trackY, trackW, trackH, function()                

                    local scrollPercent =
                        math.max(0, math.min(trackH, my - trackY)) / trackH

                    local maxScroll = contentH - box.h
                    box.scrollY = -scrollPercent * maxScroll
                    draggingBox = { box = box, lines = lines, isToast = false }

                    if isAndroidPlatform() then
                        
                        -- Hide keyboard but keep IME alive
                        love.keyboard.setTextInput(false)
                        love.keyboard.setTextInput(true)
                    elseif isDesktopPlatform() then
                        love.keyboard.setTextInput(false)
                    end
                end, 1.5)
            end
        end

    registerBoxScrollerHitbox(inputBox, wrappedInput)
    registerBoxScrollerHitbox(outputBox, wrappedOutput)

    local bw, bh = 110, 26
    local bx = inputBox.x 
    local by = inputBox.y - inputTextHeight - bh - 10

    local isReverse = (mode == "reverse")

    local isModeHover =
        mx >= bx and mx <= bx + bw and
        my >= by and my <= by + bh

    if mode == "reverse" then
        baseModeColor = {0.8,0.1,0.1}
    else
        baseModeColor = {0.2,0.2,0.25}
    end

    if isModeHover then
        baseModeColor[1] = math.min(1, baseModeColor[1] + 0.1)
        baseModeColor[2] = math.min(1, baseModeColor[2] + 0.1)
        baseModeColor[3] = math.min(1, baseModeColor[3] + 0.1)
    end

    love.graphics.setColor(baseModeColor)
    love.graphics.rectangle("fill", bx, by, bw, bh, 6)

    love.graphics.setColor(1,1,1)
    if isReverse then 
        love.graphics.setFont(reverseFont)
    else
        love.graphics.setFont(normalFont)
    end
    love.graphics.printf(isReverse and "REVERSE" or "NORMAL", bx, by+5, bw, "center")
    love.graphics.setFont(font)

    addHitbox(bx, by, bw, bh, function()

        if mode == "interpret" then
            mode = "reverse"
        else
            mode = "interpret"
        end

        updateTextWrapping()

    end, 1.5)

    love.graphics.setColor(1, 1, 1)

    local langTextW = love.graphics.getFont():getWidth(languageText)
    local langW = langTextW * 1.5
    local langH = bh
    local langX = inputBox.x + inputBox.w - langW
    local langY = by

    hoverLangUI =
        mx >= langX and mx <= langX + langW and
        my >= langY and my <= langY + langH

    local baseModeColor = {0.2,0.2,0.25}
    if hoverLangUI then
        hoverBox = false
        baseModeColor[1] = math.min(1, baseModeColor[1] + 0.1)
        baseModeColor[2] = math.min(1, baseModeColor[2] + 0.1)
        baseModeColor[3] = math.min(1, baseModeColor[3] + 0.1)
    end

    love.graphics.setColor(baseModeColor)
    love.graphics.rectangle("fill", langX, langY, langW, langH, 6)

    love.graphics.setColor(1,1,1)
    if languageText == "Demon" then
        love.graphics.setFont(demonFont)
    elseif languageText == "Ridmik" then
        love.graphics.setFont(ridmikFont)
    elseif languageText == "Blocky" then
        love.graphics.setFont(blockyFont)
    else
        love.graphics.setFont(font)
    end
    love.graphics.printf(languageText, langX, langY + 5, langW, "center")

    addHitbox(langX, langY, langW, langH, function()
        
        showLangMenu = not showLangMenu

    end, 1.5)

    if showLangMenu then
        local offsetY = langH + 5
        local drawIndex = 0

        for i, lang in ipairs(languages) do
            if i ~= currentLang then
                local y = langY + offsetY + drawIndex * (langH + 5)

                local isHover =
                    mx >= langX and mx <= langX + langW and
                    my >= y and my <= y + langH

                if love.mouse.isDown(1) and not isHover and not hoverLangUI then
                    showLangMenu = false
                end 
                local color = {0.2,0.2,0.25}

                if isHover then
                    color[1] = math.min(1, color[1] + 0.1)
                    color[2] = math.min(1, color[2] + 0.1)
                    color[3] = math.min(1, color[3] + 0.1)
                end

                love.graphics.setColor(color)
                love.graphics.rectangle("fill", langX, y, langW, langH, 6)

                -- ⭐ NEW: Set the font for THIS specific tab before printing
                if lang == "Demon" then
                    love.graphics.setFont(demonFont)
                elseif lang == "Ridmik" then
                    love.graphics.setFont(ridmikFont)
                elseif lang == "Blocky" then
                    love.graphics.setFont(blockyFont)
                else
                    love.graphics.setFont(font)
                end

                love.graphics.setColor(1,1,1)
                love.graphics.printf(lang, langX, y + 5, langW, "center")
                
                -- ⭐ RESET back to normal so the next loop/UI element is safe
                love.graphics.setFont(font)

                addHitbox(langX, y, langW, langH, function()
                    currentLang = i
                    showLangMenu = false
                    findEldaMap()
                    updateTextWrapping()
                end, 1.5)

                drawIndex = drawIndex + 1
            end
        end
    end
    
    --SAVE BUTTON
    local enabled = rawText ~= nil and rawText ~= "" --and not love.keyboard.hasTextInput()
    local alpha = enabled and 1 or 0.35
    
    -- Hover should work ONLY when enabled
    local hover = false
    if enabled then
        hover = (mx >= proceedBtn.x and mx <= proceedBtn.x + proceedBtn.w and
                my >= proceedBtn.y and my <= proceedBtn.y + proceedBtn.h)
    end

    local baseColor = hover and {0.2, 0.8, 0.2} or {0.1, 0.5, 0.1}

    love.graphics.setColor(baseColor[1], baseColor[2], baseColor[3], alpha)
    love.graphics.rectangle("fill", proceedBtn.x, proceedBtn.y, proceedBtn.w, proceedBtn.h, 10)

    love.graphics.setColor(1, 1, 1, alpha)
    love.graphics.setFont(titleFont)
    love.graphics.printf(
        "SAVE DATA",
        proceedBtn.x,
        proceedBtn.y + (proceedBtn.h / 2 - 8),
        proceedBtn.w,
        "center"
    )
    love.graphics.setFont(font)

    -- Hitbox only when enabled
    if enabled then
        addHitbox(proceedBtn.x, proceedBtn.y, proceedBtn.w, proceedBtn.h,
        function()

            selectionActionToast = nil  
            selectionStart = nil
            selectionEnd =  nil
            saveProcess()
        end, 1)
    end

    if statusTimer > 0 then
        love.graphics.setColor(statusColor[1], statusColor[2], statusColor[3], math.min(1, statusTimer))
        love.graphics.setFont(titleFont)
        love.graphics.printf(statusMsg, 0, tabH + 10, sw, "center")
        love.graphics.setFont(font)
    end

    -- ===================== SAVES TAB =====================
    elseif currentTab == "saves" then
        love.graphics.setColor(1, 1, 1)
        love.graphics.setFont(titleFont)
        local label = "Path =>  [ "
        love.graphics.print(label, path.x, path.y)
        local offset = love.graphics.getFont():getWidth(label)
        love.graphics.setColor(0.4, 1, 0.4)
        local font = love.graphics.getFont()
        local screenW = sw

        -- Measure path width
        local pathText = currentPath ~= "" and currentPath or "/"

        local pathMaxWidth = screenW - path.x - 40

        -- Clamp path display if too long
        local drawPath = pathText
        while font:getWidth(drawPath) > pathMaxWidth and #drawPath > 4 do
            drawPath = drawPath:sub(1, -2)
        end

        if drawPath ~= pathText then
            drawPath = drawPath .. "…"
        end

        love.graphics.setColor(0.4, 1, 0.4)
        love.graphics.print(drawPath, path.x + offset, path.y)
        local pathWidth = love.graphics.getFont():getWidth(currentPath)
        love.graphics.setColor(1, 1, 1)
        love.graphics.print(" ]", path.x + offset + pathWidth, path.y)

        local isAndroid = isAndroidPlatform() or (love.system.getOS() == "Android")
        local rootPath = getSavesRootPath()

        if currentPath ~= rootPath and currentPath ~= "" then
            local offset = 25
            backBtn.x, backBtn.y = path.x, path.y + offset
            local isBackHover =
                mx >= backBtn.x and mx <= backBtn.x + backBtn.w and
                my >= backBtn.y and my <= backBtn.y + backBtn.h

            if isBackHover then
                love.graphics.setColor(0.5, 0.5, 0.5)
            else
                love.graphics.setColor(0.3, 0.3, 0.3)
            end

            love.graphics.rectangle("fill", backBtn.x, backBtn.y, backBtn.w, backBtn.h, 4)

            love.graphics.setColor(1,1,1)
            love.graphics.printf("< BACK", backBtn.x, backBtn.y + 5, backBtn.w, "center")
            addHitbox(backBtn.x, backBtn.y, backBtn.w, backBtn.h, function()
                if isAndroid then
                    local parentPath = currentPath:match("(.*)/[^/]+$") or ""
                    loadDirectory(parentPath)
                else
                    local sep = (isDesktopPlatform()) and "\\" or "/"
                    local masterPath = getDesktopPath() .. sep .. "DEMON INTERPRETER FILES"
                    if currentPath ~= masterPath then
                        local parentPath = currentPath:match("(.*)" .. sep .. "[^" .. sep .. "]+$") or masterPath
                        loadRealDirectory(parentPath)
                    end
                end
            end, 1.5)
        end

        local pathFontH = font:getHeight()
        local listMargin = 15
        local rootPath = getSavesRootPath()
        local isAtRoot = (currentPath == rootPath or currentPath == "")
        local listYStart

        if isAtRoot then
            listYStart = path.y + pathFontH + listMargin
        else
            listYStart = backBtn.y + backBtn.h + listMargin
        end
        local itemH = 40
        local listH = sh - 40 - listYStart

        -- REMOVE button dimensions
        local removeBtnW = 60
        local removeBtnH = 26

        love.graphics.setScissor(0, listYStart, sw, listH)

        for i, item in ipairs(dirItems) do

            local drawY = listYStart + savesScrollY + (i - 1) * itemH

            if drawY + itemH > listYStart and drawY < sh - 40 then
                        
                local listRightBound = sw - SAVE_LIST_RIGHT_MARGIN
                local hover =
                    mx >= SAVE_LIST_LEFT_MARGIN and
                    mx <= listRightBound and
                    my >= drawY and
                    my <= drawY + itemH

                local rbX = listRightBound - removeBtnW - 6
                local rbY = drawY + math.floor((itemH - 5 - removeBtnH) / 2)

                local removeHover =
                mx >= rbX and mx <= rbX + removeBtnW and
                my >= rbY and my <= rbY + removeBtnH

                if hover and not removeHover then
                    love.graphics.setColor(getHoverColor("row"))
                else
                    love.graphics.setColor(getDefaultColor("row_idle"))
                end

                love.graphics.rectangle(
                    "fill",
                    SAVE_LIST_LEFT_MARGIN,
                    drawY,
                    listRightBound - SAVE_LIST_LEFT_MARGIN,
                    itemH - 5,
                    4
                )

                -- Label
                local setFont = "[" .. item.index .. "]"
                local indexColW = love.graphics.getFont():getWidth(setFont)
                -- index column
                love.graphics.setColor(0.8, 0.8, 0.8)
                love.graphics.setFont(titleFont)
                love.graphics.printf(
                    setFont,
                    SAVE_LIST_LEFT_MARGIN + 10,
                    drawY + 8,
                    indexColW,
                    "left"
                )                
                
                if item.type == "directory" then
                    love.graphics.setColor(0.9, 0.8, 0.2)
                    love.graphics.setFont(fileFont)
                else
                    love.graphics.setColor(0.7, 0.9, 1)
                    love.graphics.setFont(itemFont)
                end

                love.graphics.print(
                    item.name,
                    SAVE_LIST_LEFT_MARGIN + indexColW + 15,
                    drawY + 8
                )
                love.graphics.setFont(font)
                if removeHover then
                    love.graphics.setColor(0.75, 0.2, 0.2)
                else
                    love.graphics.setColor(0.55, 0.08, 0.08)
                end

                love.graphics.rectangle("fill", rbX, rbY, removeBtnW, removeBtnH, 4)
                love.graphics.setColor(1, 0.55, 0.55)
                love.graphics.setFont(titleFont)
                love.graphics.printf("REMOVE", rbX, rbY + 7, removeBtnW, "center")
                love.graphics.setFont(font)

                local capturedItem = item

                addHitbox(rbX, rbY, removeBtnW, removeBtnH, function()
                    
                    print("REMOVE BUTTON CLICKED") -- ⭐ DEBUG HERE
                    openDeleteConfirm(capturedItem)
                end, 1.5)

                -- Row open hitbox (left portion, stops before remove btn)
                addHitbox(SAVE_LIST_LEFT_MARGIN,
                        drawY,
                        listRightBound - SAVE_LIST_LEFT_MARGIN - 4,
                        itemH - 5, 
                        function()
                    
                    
                    if love.system.getOS() ~= "Android" then
                        if capturedItem.type == "directory" then
                            
                            loadRealDirectory(capturedItem.path)
                        else
                            local f = io.open(capturedItem.path, "r")
                            if f then
                                print("FILE BUTTON CLICKED") -- ⭐ DEBUG HERE
                                -- ⭐ Use existing modal bools only
                                if toastPopup then
                                    print("MODAL ACTIVE — CLOSE CURRENT FILE FIRST")
                                    return
                                end

                                local content = f:read("*a"); f:close(); openToast(capturedItem.name, content)

                            end
                        end
                    else
                        if capturedItem.type == "directory" then
                            
                            loadDirectory(capturedItem.path)
                        else
                            
                            print("FILE BUTTON CLICKED (ANDROID)")
                            -- ⭐ Use existing modal bools only
                            if toastPopup then
                                print("MODAL ACTIVE — CLOSE CURRENT FILE FIRST")
                                return
                            end

                            
                            local content = love.filesystem.read(capturedItem.path)
                            if content then openToast(capturedItem.name, content) end
                        end
                    end
                end, 1)
            end
        end

        love.graphics.setScissor()

        if #dirItems == 0 then
            love.graphics.setColor(0.5, 0.5, 0.5)
            love.graphics.setFont(titleFont)
            love.graphics.printf("Directory is empty.", 0, sh / 2, sw, "center")
            love.graphics.setFont(font)
        end

        -- Saves scroller
        local contentH = #dirItems * itemH
        if contentH > listH then
            local info = getSavesScrollerInfo()
            if info then
                local trackX = sw - SAVE_LIST_RIGHT_MARGIN * SAVE_SCROLLBAR_CENTER_X_RATIO  
                
                love.graphics.setColor(0.1,0.1,0.1)        
                love.graphics.rectangle(
                    "fill",
                    trackX - SCROLLBAR_TRACK_W/2,
                    info.trackY,
                    SCROLLBAR_TRACK_W,
                    info.trackH,
                    SCROLLBAR_RADIUS
                )

                local thumbHover =
                    mx >= trackX - SCROLLBAR_THUMB_W/2 and
                    mx <= trackX + SCROLLBAR_THUMB_W/2 and
                    my >= info.thumbY and
                    my <= info.thumbY + info.thumbH

                if thumbHover then
                    love.graphics.setColor(0.6,0.8,1)
                else
                    love.graphics.setColor(0.4,0.6,1)
                end
                love.graphics.rectangle(
                    "fill",
                    trackX - SCROLLBAR_THUMB_W/2,
                    info.thumbY,
                    SCROLLBAR_THUMB_W,
                    info.thumbH,
                    SCROLLBAR_RADIUS
                )
            end
        end
    end

    -- ===================== TOAST POPUP =====================
    if toastPopup then
        
        local viewH = toastPopup.h - 95   -- ⭐ MOVE THIS UP, before highlight block
        local contentH = #toastPopup.lines * lineH + padding * 2
        -- ... rest of drawing ...
        love.graphics.setColor(0, 0, 0, 0.8)
        love.graphics.rectangle("fill", 0, 0, sw, sh)
        love.graphics.setColor(0.1, 0.1, 0.12)
        love.graphics.rectangle("fill", toastPopup.x, toastPopup.y, toastPopup.w, toastPopup.h, 10)
        addHitbox(toastPopup.x, toastPopup.y, toastPopup.w, toastPopup.h, function() end, 2) -- Block interactions behind the toast
        love.graphics.setColor(0.3, 0.6, 1)
        love.graphics.rectangle("line", toastPopup.x, toastPopup.y, toastPopup.w, toastPopup.h, 10)
        love.graphics.setColor(1, 1, 1)
        love.graphics.setFont(titleFont)
        love.graphics.printf(toastPopup.title, toastPopup.x, toastPopup.y + 15, toastPopup.w, "center")
        love.graphics.setFont(font)
        love.graphics.line(toastPopup.x + 20, toastPopup.y + 40, toastPopup.x + toastPopup.w - 20, toastPopup.y + 40)
        love.graphics.setScissor(toastPopup.x, toastPopup.y + 45, toastPopup.w, toastPopup.h - 95)

        -- Selection highlight
        if toastSelectionStart and toastSelectionEnd and toastSelectionBox then
            local s, e = normalizeSelection(toastSelectionStart, toastSelectionEnd)
            local font = love.graphics.getFont()

            love.graphics.setScissor(toastPopup.x, toastPopup.y + 45, toastPopup.w - SCROLLBAR_TRACK_W, viewH)

            for line = s.line, e.line do
                local lineText = toastPopup.lines[line]
                if not lineText then goto continue2 end

                local startChar = 1
                local endChar = utf8.len(lineText) or 0
                if line == s.line then startChar = s.char end
                if line == e.line then endChar = e.char end

                local prefixText = utf8_sub(lineText, 1, startChar - 1)
                local selText    = utf8_sub(lineText, startChar, endChar)

                local px = toastPopup.x + 20 + font:getWidth(prefixText)
                local py = toastPopup.y + 50 + toastPopup.scrollY + (line - 1) * lineH

                love.graphics.setColor(0.3, 0.5, 1, 0.4)
                love.graphics.rectangle("fill", px, py, font:getWidth(selText), lineH)

                ::continue2::
            end
            love.graphics.setScissor()
        end

        love.graphics.setColor(0.9, 0.9, 0.9)
        for i, line in ipairs(toastPopup.lines) do
            local drawY = toastPopup.y + 50 + toastPopup.scrollY + (i - 1) * lineH
            if drawY + lineH > toastPopup.y + 45 and drawY < toastPopup.y + toastPopup.h - 50 then
                love.graphics.print(line, toastPopup.x + 20, drawY)
            end
        end
        -- Selection action toast (COPY / COPY ALL)
        if toastSelectionActionToast and toastSelectionStart and toastSelectionEnd and not love.mouse.isDown(1) then
            local t = toastSelectionActionToast

            love.graphics.setColor(0.08, 0.08, 0.1, 0.95)
            love.graphics.rectangle("fill", t.x, t.y, t.w, t.h, 6)
            love.graphics.setColor(1, 1, 1)

            local font = love.graphics.getFont()
            local padding_inner = 12
            local copyW = font:getWidth("COPY") + padding_inner * 2
            local copyAllW = font:getWidth("COPY ALL") + padding_inner * 2

            love.graphics.setFont(titleFont)
            love.graphics.printf("COPY", t.x, t.y + 8, copyW, "center")
            love.graphics.printf("COPY ALL", t.x + copyW, t.y + 8, copyAllW, "center")
            love.graphics.setFont(font)

            -- Build toast raw text for copy
            local toastFullText = table.concat(toastPopup.lines, "\n")

            love.graphics.setColor(1, 0, 1)
            addHitbox(t.x, t.y, copyW, t.h, function()
                -- Copy selected region from toast lines
                local s, e = normalizeSelection(toastSelectionStart, toastSelectionEnd)
                local result = ""
                for line = s.line, e.line do
                    local lineText = toastPopup.lines[line] or ""
                    local sc = (line == s.line) and s.char or 1
                    local ec = (line == e.line) and e.char or (utf8.len(lineText) or 0)
                    result = result .. utf8_sub(lineText, sc, ec)
                    if line < e.line then result = result .. "\n" end
                end
                love.system.setClipboardText(result)
                toastSelectionActionToast = nil
                toastSelectionStart = nil
                toastSelectionEnd = nil
                showFadeMessage("COPIED SELECTED BLOCK")
            end, 3)

            love.graphics.setColor(1, 1, 0)
            addHitbox(t.x + copyW, t.y, copyAllW, t.h, function()
                love.system.setClipboardText(toastFullText)
                toastSelectionActionToast = nil
                toastSelectionStart = nil
                toastSelectionEnd = nil
                showFadeMessage("COPIED ALL")
            end, 3)
        end
        love.graphics.setScissor()
        local contentH = #toastPopup.lines * lineH + padding * 2
        local viewH = toastPopup.h - 95
        if contentH > viewH then
            local thumbH = math.max(30, (viewH / contentH) * viewH)
            local scrollPercent = -toastPopup.scrollY / (contentH - viewH)
            local thumbY = toastPopup.y + 45 + (viewH - thumbH) * scrollPercent
            local trackX = toastPopup.x + toastPopup.w - SCROLLBAR_TRACK_W

            love.graphics.setColor(0.1, 0.1, 0.1)
            love.graphics.rectangle(
                "fill",
                trackX,
                toastPopup.y + 45,
                SCROLLBAR_TRACK_W,
                viewH,
                SCROLLBAR_RADIUS
            )

            local thumbLeft = trackX + (SCROLLBAR_TRACK_W - SCROLLBAR_THUMB_W) / 2
            local thumbHover =
                mx >= thumbLeft and
                mx <= thumbLeft + SCROLLBAR_THUMB_W and
                my >= thumbY and
                my <= thumbY + thumbH

            if thumbHover then
                love.graphics.setColor(0.6, 0.8, 1)
            else
                love.graphics.setColor(0.4, 0.6, 1)
            end
            love.graphics.rectangle(
                "fill",
                trackX + (SCROLLBAR_TRACK_W - SCROLLBAR_THUMB_W) / 2,
                thumbY,
                SCROLLBAR_THUMB_W,
                thumbH,
                SCROLLBAR_RADIUS
            )
        end
        local btnW, btnH = 100, 30
        local btnX = toastPopup.x + (toastPopup.w / 2) - (btnW / 2)
        local btnY = toastPopup.y + toastPopup.h - 40
        local closeHover =
            mx >= btnX and mx <= btnX + btnW and
            my >= btnY and my <= btnY + btnH

        if closeHover then
            love.graphics.setColor(0.75, 0.25, 0.25)
        else
            love.graphics.setColor(0.6, 0.2, 0.2)
        end
        love.graphics.rectangle("fill", btnX, btnY, btnW, btnH, 5)
        love.graphics.setColor(1, 1, 1)
        love.graphics.setFont(titleFont)
        love.graphics.printf("CLOSE", btnX, btnY + 7, btnW, "center")
        love.graphics.setFont(font)
        addHitbox(btnX, btnY, btnW, btnH, function() toastPopup = nil end, 2.5)
    end

    -- ===================== DELETE CONFIRM DIALOG =====================
    if deleteConfirm then
        local dc = deleteConfirm

        -- Dim overlay
        love.graphics.setColor(0, 0, 0, 0.78)
        love.graphics.rectangle("fill", 0, 0, sw, sh)

        -- Dialog panel
        love.graphics.setColor(0.13, 0.08, 0.08)
        love.graphics.rectangle("fill", dc.x, dc.y, dc.w, dc.h, 8)
        love.graphics.setColor(0.85, 0.2, 0.2)
        love.graphics.rectangle("line", dc.x, dc.y, dc.w, dc.h, 8)

        -- Title
        love.graphics.setColor(1, 0.3, 0.3)
        love.graphics.setFont(titleFont)
        love.graphics.printf("⚠  DELETE ENTRY?", dc.x, dc.y + 10, dc.w, "center")
        love.graphics.setFont(font)
        -- Item name (truncated if needed)
        love.graphics.setColor(0.95, 0.95, 0.95)
        local font = love.graphics.getFont()
        local nameLabel = dc.item.name
        while font:getWidth(nameLabel) > dc.w - 28 and #nameLabel > 4 do
            nameLabel = nameLabel:sub(1, -2)
        end
        if nameLabel ~= dc.item.name then nameLabel = nameLabel .. "…" end
        love.graphics.setFont(itemFont)
        love.graphics.printf(nameLabel, dc.x, dc.y + 35, dc.w, "center")
        love.graphics.setFont(font)
        -- Warning text
        love.graphics.setColor(0.55, 0.55, 0.55)
        love.graphics.setFont(titleFont)
        love.graphics.printf("This cannot be undone.", dc.x, dc.y + 58, dc.w, "center")
        love.graphics.setFont(font)

        -- YES / NO buttons
        local gap = 10
        local bw = (dc.w - gap * 3) / 2
        local bh = 36
        local btnY = dc.y + dc.h - bh - 12

        local yesX = dc.x + gap
        local noX  = dc.x + gap + bw + gap

        -- YES - DELETE (red)
        love.graphics.setColor(0.72, 0.08, 0.08)
        love.graphics.rectangle("fill", yesX, btnY, bw, bh, 6)
        love.graphics.setColor(1, 1, 1)
        love.graphics.setFont(titleFont)
        love.graphics.printf("YES, DELETE", yesX, btnY + 10, bw, "center")
        love.graphics.setFont(font)

        -- NO - KEEP (green)
        love.graphics.setColor(0.12, 0.42, 0.18)
        love.graphics.rectangle("fill", noX, btnY, bw, bh, 6)
        love.graphics.setColor(1, 1, 1)
        love.graphics.setFont(titleFont)
        love.graphics.printf("NO, KEEP", noX, btnY + 10, bw, "center")
        love.graphics.setFont(font)
        
        local capturedItem = dc.item
        addHitbox(yesX, btnY, bw, bh, function()
            deleteItem(capturedItem)
            deleteConfirm = nil                        
        end, 2.5)
        addHitbox(noX, btnY, bw, bh, function()
            deleteConfirm = nil            
        end, 2.5)
    end

    -- ===================== BOTTOM TABS =====================
    love.graphics.setColor(0.05, 0.05, 0.05)
    love.graphics.rectangle("fill", 0, tabY, sw, tabH)

    local t1Hover = mx < sw/2 and my >= tabY
    love.graphics.setColor(currentTab == "interpreter" and {0.2, 0.4, 0.8} or (t1Hover and {0.15, 0.2, 0.3} or {0.1, 0.1, 0.1}))
    love.graphics.rectangle("fill", 0, tabY, sw / 2, tabH)
    love.graphics.setColor(1, 1, 1)
    love.graphics.setFont(titleFont)
    love.graphics.printf("INTERPRETER", 0, tabY + 12, sw / 2, "center")
    love.graphics.setFont(font)
    addHitbox(0, tabY, sw/2, tabH, function()
        currentTab = "interpreter"
        local rootPath = getSavesRootPath()
        if isAndroidPlatform() then loadDirectory(rootPath) else loadRealDirectory(rootPath) end
    end,1)

    local t2Hover = mx >= sw/2 and my >= tabY
    love.graphics.setColor(currentTab == "saves" and {0.2, 0.4, 0.8} or (t2Hover and {0.15, 0.2, 0.3} or {0.1, 0.1, 0.1}))
    love.graphics.rectangle("fill", sw / 2, tabY, sw / 2, tabH)
    love.graphics.setColor(1, 1, 1)
    love.graphics.setFont(titleFont)
    love.graphics.printf("SAVES (OS)", sw / 2, tabY + 12, sw / 2, "center")
    love.graphics.setFont(font)
    addHitbox(sw/2, tabY, sw/2, tabH, function()
        currentTab = "saves"
        if isAndroidPlatform() then loadDirectory(currentPath) else loadRealDirectory(currentPath) end
    end,1)

    love.graphics.setColor(0.3, 0.3, 0.3)
    love.graphics.line(sw / 2, tabY, sw / 2, sh)
    love.graphics.line(0, tabY, sw, tabY)

    love.graphics.setColor(1, 1, 1)
    if toastFadeMsg then
        local alpha = math.min(1, toastFadeTimer)

        love.graphics.setColor(1, 1, 1, alpha)

        love.graphics.printf(
            toastFadeMsg,
            0,
            sh * 0.45,
            sw,
            "center"
        )
    end

    love.graphics.print("TEXT: " .. rawText, 100, 100)
    love.graphics.print("IND: " .. caretIndex, 300, 100)
    

    if isAndroidPlatform() then
        Keyboard.draw()    
        for _, f in ipairs(textFields) do f:draw() end
    end 


end
