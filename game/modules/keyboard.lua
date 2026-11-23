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

    if not active
    or not love.mouse.isDown(1)
    or not pendingKey then

        pressedKey = nil
        return
    end

    -------------------------------------------------
    -- RAPID BACKSPACE
    -------------------------------------------------

    if pendingKey.key == "backspace" then

        repeatTimer = repeatTimer + dt

        -- Wait initial hold delay
        if repeatTimer >= repeatDelay then

            -- Convert timer into rapid repeating
            while repeatTimer >= repeatDelay + repeatInterval do

                if callback then
                    callback("backspace")
                end

                repeatTimer =
                    repeatTimer - repeatInterval
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