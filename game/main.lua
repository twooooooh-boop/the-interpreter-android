local utf8 = require("utf8")
local Keyboard = require "modules.keyboard"
local Caret = require "modules.caret"
local TextField = require "modules.textfield"
local Keymap = require "modules.keymap"       -- editable letter<->key tables
local Settings = require "modules.settings"   -- SETTINGS tab

local sw, sh = love.graphics.getDimensions()
local mx, my = love.mouse.getPosition()
local font = love.graphics.newFont("fonts/NotoSans-Regular.ttf", 13)
local demonFont = love.graphics.newFont("fonts/DeathMohawk.otf", 13)
local normalFont = love.graphics.newFont("fonts/Via Appia.ttf", 13)
local reverseFont = love.graphics.newFont("fonts/Ithornet.ttf", 13)
local ridmikFont = love.graphics.newFont("fonts/Andarion-Regular.ttf", 13)
local blockyFont = love.graphics.newFont("fonts/techno_hideo_bold.ttf", 13)
local titleFont = love.graphics.newFont("fonts/One Slice.otf", 13)
local itemFont = love.graphics.newFont("fonts/Maloney.ttf", 13)
local fileFont = love.graphics.newFont("fonts/Sephora & Hayden.ttf", 13)

local FORCE_ANDROID = false
local rawText = ""
local decodedText = ""
local inputActive = false
local caretIndex = 0
local mode = "interpret"
local languages = {"Demon", "Ridmik", "Blocky"}
local currentLang = 1
local showLangMenu = false
local hoverLangUI = false
local ctrlDown = false
local padding = 20
local lineH = 20
local inputBox, outputBox, proceedBtn, clearBtn, clearBtnOut
local draggingBox = nil
local statusMsg = ""
local statusTimer = 0
local statusColor = {1, 1, 1}
local currentTab = "interpreter"
local currentPath = ""
local savesScrollY = 0
local toastPopup = nil
local draggingSavesScroll = false
local deleteConfirm = nil
local SCROLLBAR_TRACK_W = 25
local SCROLLBAR_THUMB_W = 25
local SCROLLBAR_RADIUS = 4
local SAVE_LIST_LEFT_MARGIN = 20
local SAVE_LIST_RIGHT_MARGIN = 40   -- ← SPACE RESERVED FOR SCROLLER + BUTTONS
local SAVE_SCROLLBAR_CENTER_X_RATIO = 0.5
local DEBUG_SELECTION_TOAST = false
local nextHitboxId = 1
local selectionStart = nil
local selectionEnd = nil
local selectionBox = nil
local selectionActionToast = nil
local selectionPending = false
local selectionAnchor = nil       -- stores click pos before drag confirms
local selectionDragStarted = false
local toastFadeMsg = nil
local toastFadeTimer = 0
local toastSelectionStart = nil
local toastSelectionEnd = nil
local toastSelectionActionToast = nil
local toastSelectionBox = nil  -- a fake "box" proxy for the toast content area

local encodeMap = {}
local historyStack = {}
local redoStack = {}
local wrappedInput = {}
local wrappedOutput = {}
local dirItems = {}
local hitboxes = {}
local backBtn = { x = 20, y = 45, w = 80, h = 25 }
local path = { x = 20, y = 50 }
local eldaMaps = {

    --Made from M4hdi
    demon = {

        ["Δ"]="a", ["б"]="b", ["ς"]="c", ["д"]="d", ["э"]="e",
        ["ц"]="f", ["δ"]="g", ["ю"]="h", ["ι"]="i", ["л"]="j",
        ["κ"]="k", ["г"]="l", ["щ"]="m", ["й"]="n", ["Θ"]="o",
        ["p"]="p", ["φ"]= "q", ["я"]="r",["ʂ"]="s", ["т"]="t",
        ["ч"]="u", ["v"]="v", ["ω"]="w", ["x"] ="x",["ý"]="y",
                                         ["Ξ"]="x", ["(z)"]="z",

        ["A"]="A", ["Б"]="B", ["C"]="C", ["Д"]="D", ["Ә"]="E",
        ["Ц"]="F", ["ɣ"]="G", ["Ю"]="H", ["Ы"]="I", ["Л"]="J",
        ["К"]="K", ["Г"]="L", ["Σ"]="M", ["И"]="N", ["Ω"]="O",
        ["r"]="P",["(Q)"]="Q",["Я"]="R", ["S"]="S", ["γ"]="T",
        ["Ч"]="U", ["Λ"]="V", ["ʒ"] = "W", ["ϰ"]="X",["λ"]="Y",
        ["(Z)"]="Z"

    },

    -- Made via Ridmik(17.2.1) keyboard's non alphanumeric's Poaition where the Letters would have existed.
    ridmik = {

        ["£"]="a",   ["\\"]="b",  ["©"]="c",   ["℅"]="d",   ["|"]="e",
        ["^"]="f",   ["°"]="g",   ["_"]="h",   ["×"]="i",   ["="]="j",
        ["["]="k",   ["]"]="l",   [">"]="m",   ["<"]="n",   ["{"]="o",
        ["}"]="p",   ["~"]="q",   ["ঽ"]="r",   ["$"]="s",   ["√"]="t",
        ["÷"]="u",   ["™"]="v",   ["`"]="w",   ["®"]="x",   ["π"]="y",
        ["‌!"]="z",

        ["£"]="A",   ["\\"]="B",  ["©"]="C",   ["℅"]="D",   ["|"]="E",
        ["^"]="F",   ["°"]="G",   ["_"]="H",   ["×"]="I",   ["="]="J",
        ["["]="K",   ["]"]="L",   [">"]="M",   ["<"]="N",   ["{"]="O",
        ["}"]="P",   ["~"]="Q",   ["ঽ"]="R",   ["$"]="S",   ["√"]="T",
        ["÷"]="U",   ["™"]="V",   ["`"]="W",   ["®"]="X",   ["π"]="Y",
        ["‌!"]="Z"

    },

    --Made via turning normal letters to blocky,then putting in each segment.
    blocky = {

        ["^0"]="a",      ["|o"]="b",     ["<"]="c",       ["o|"]="d",     ["<-"]="e",
        ["|^-"]="f",     ["&"]="g",      ["h"]="h",       ["i"]="i",      ["j"]="j",
        ["|/_"]="k",     ["l"]="l",      ["|^||^|"]="m",  ["|^|"]="n",    ["o"]="o",
        ["|O"]="p",      ["O|/"]="q",    ["|^"]="r",      ["-<_"]="s",    ["t"]="t",
        ["u"]="u",       ["v"]="v",      ["w"]="w",       ["*"]="x",      ["v/"]="y",
        ["->_"]="z",

        ["|=|"]="A",     ["|_|-|_"]="B", ["-|_"]="C",     ["|-|_"]="D",   ["|_-_"]="E",
        ["|-_"]="F",     ["-|_|-"]="G",  ["H"]="H",       ["I"]="I",      ["_|-|"]="J",
        ["|/\\"]="K",    ["|_"]="L",     ["M"]="M",       ["N"]="N",      ["O"]="O",
        ["|0"]="P",      ["O\\"]="Q",    ["|-|_\\"]="R",  ["-|_|_"]="S",  ["T"]="T",
        ["U"]="U",       ["V"]="V",      ["W"]="W",       ["\\/"]="X",    ["\\/|"]="Y",
        ["-/_"]="Z"

    }
}
local eldaMapLang, eldaMapVersion = nil, -1
eldaKeyLens = {}

function findEldaMap()
    local key = languages[currentLang]:lower()
    local v = Keymap.version()
    if key == eldaMapLang and v == eldaMapVersion then return end   -- nothing changed
    eldaMapLang, eldaMapVersion = key, v
    eldaMap, encodeMap, eldaKeyLens = Keymap.build(key)
end

function showFadeMessage(msg)
    toastFadeMsg = msg
    toastFadeTimer = 2.0   -- display duration
end

function isAndroidPlatform()
    return FORCE_ANDROID or love.system.getOS() == "Android"
end

function isDesktopPlatform()
    return not isAndroidPlatform()
end

function addHitbox(x, y, w, h, onClick, priority)
    local id = nextHitboxId
    nextHitboxId = nextHitboxId + 1

    hitboxes[id] = {
        id = id,
        x = x,
        y = y,
        w = w,
        h = h,
        onClick = onClick,
        priority = priority or 0
    }

    return id
end

function clearHitboxes()
    hitboxes = {}
    nextHitboxId = 1
end

function resolveHitbox(mx, my)
    local candidates = nil
    local best = nil

    for _, box in pairs(hitboxes) do
        if mx >= box.x and mx <= box.x + box.w and
           my >= box.y and my <= box.y + box.h then

            if not best then
                best = box
            else
                if box.priority > best.priority then
                    best = box
                elseif box.priority == best.priority then
                    -- newer hitbox wins if priority equal
                    if box.id > best.id then
                        best = box
                    end
                end
            end
        end
    end

    return best
end

function decode(input)
    local out = {}
    local unknownIcons = {}
    local unknownCount = 0
    local i, n = 1, #input
    while i <= n do
        -- 1) try every known key at this position, longest first
        local matched = false
        for _, len in ipairs(eldaKeyLens) do
            if i + len - 1 <= n then
                local letter = eldaMap[input:sub(i, i + len - 1)]
                if letter then
                    out[#out + 1] = letter
                    i = i + len
                    matched = true
                    break
                end
            end
        end
        -- 2) otherwise handle one character exactly like before
        if not matched then
            local char = input:match("^[%z\1-\127\194-\244][\128-\191]*", i)
            if not char then
                i = i + 1                       -- stray byte, skip it
            else
                i = i + #char
                if char:match("[%w%p%s]") then
                    out[#out + 1] = char
                else
                    if not unknownIcons[char] then
                        unknownCount = unknownCount + 1
                        unknownIcons[char] = unknownCount
                    end
                    out[#out + 1] = "[" .. unknownIcons[char] .. "]"
                end
            end
        end
    end
    return table.concat(out)
end

function encodeNormal(text)
    local result = ""
    for i = 1, #text do
        local char = text:sub(i,i)
        local lower = char:lower()
        -- try exact case first, fall back to lowercase lookup
        result = result .. (encodeMap[char] or encodeMap[lower] or encodeMap[char:upper()] or char)
    end
    return result
end

function isInteractionBlocked(x, y)

    -- Popup modal lock
    if toastPopup then
        local px, py = toastPopup.x, toastPopup.y
        local pw, ph = toastPopup.w, toastPopup.h

        if not (x >= px and x <= px + pw and y >= py and y <= py + ph) then
            return true
        end
    end

    -- Delete confirm modal lock
    if deleteConfirm then
        local dx, dy = deleteConfirm.x, deleteConfirm.y
        local dw, dh = deleteConfirm.w, deleteConfirm.h

        if not (x >= dx and x <= dx + dw and y >= dy and y <= dy + dh) then
            return true
        end
    end

    return false
end

function setupUI()

sw, sh = love.graphics.getDimensions()
mx, my = love.mouse.getPosition()

local margin = sh * 0.03
local tabSafeOffset = 50   -- ⭐ keeps panels away from tabs

-- ================= INPUT BOX =================
inputBox = inputBox or { active = false, scrollY = 0 }

inputBox.w = sw * 0.9
inputBox.h = sh * 0.32
inputBox.x = sw * 0.05
inputBox.y = sh * 0.10 + tabSafeOffset

-- ================= CLEAR BUTTON =================
clearBtn = clearBtn or { w = 80, h = 22 }

clearBtn.w = 80
clearBtn.h = 22
clearBtn.x = inputBox.x + inputBox.w - clearBtn.w
clearBtn.y = inputBox.y - clearBtn.h - 4

-- ================= OUTPUT BOX =================
outputBox = outputBox or { active = false, scrollY = 0 }

outputBox.w = sw * 0.9
outputBox.h = sh * 0.32
outputBox.x = sw * 0.05
outputBox.y = inputBox.y + inputBox.h + margin

-- ⭐ Push everything slightly down so tabs are not touched
local tabHeight = 40
local bottomSafety = tabHeight + 10

if outputBox.y + outputBox.h > sh - bottomSafety then
    local diff = (outputBox.y + outputBox.h) - (sh - bottomSafety)

    inputBox.y = inputBox.y - diff * 0.5
    outputBox.y = outputBox.y - diff * 0.5
end

-- ================= PROCEED BUTTON =================
proceedBtn = proceedBtn or {}

proceedBtn.w = sw * 0.3
proceedBtn.h = sh * 0.07
proceedBtn.x = (sw - proceedBtn.w) / 2
proceedBtn.y = outputBox.y + outputBox.h + margin
end

function updateTextWrapping()
    local font = love.graphics.getFont()

    local _, linesIn = font:getWrap(rawText, inputBox.w - (padding * 2) - 20)
    wrappedInput = linesIn

    if mode == "interpret" then
        decodedText = decode(rawText)
    else
        decodedText = encodeNormal(rawText)
    end

    local _, linesOut = font:getWrap(decodedText, outputBox.w - (padding * 2) - 20)
    wrappedOutput = linesOut
end

function openToast(title, content)
    local font = love.graphics.getFont()
    local pw = sw * 0.85
    local _, lines = font:getWrap(content, pw - 40)
    toastPopup = {
        title = title, lines = lines, scrollY = 0,
        w = pw, h = sh * 0.75,
        x = (sw - pw) / 2, y = (sh - sh * 0.75) / 2
    }
end

function getDesktopPath()
    local userProfile = os.getenv("USERPROFILE") or os.getenv("HOME")
    if isDesktopPlatform() then
        local standard = userProfile .. "\\Desktop"
        local oneDrive = userProfile .. "\\OneDrive\\Desktop"
        return (os.rename(standard, standard)) and standard or oneDrive
    end
    return userProfile .. "/Desktop"
end

function loadRealDirectory(path)
    currentPath = path
    dirItems = {}
    savesScrollY = 0
    local checkCmd = isDesktopPlatform() and ('if exist "' .. path .. '" (exit 0) else (exit 1)') or ('test -d "' .. path .. '"')
    local success = os.execute(checkCmd)
    if success ~= 0 and success ~= true then return end
    local command = isDesktopPlatform() and ('dir "' .. path .. '" /b') or ('ls -1 "' .. path .. '"')
    local p = io.popen(command)
    if not p then return end
    for item in p:lines() do
        local sep = isDesktopPlatform() and "\\" or "/"
        local fullPath = path .. sep .. item
        local isDir
        if isDesktopPlatform() then
            local attr = io.popen('powershell -NoProfile -Command "(Get-Item \\"' .. fullPath .. '\\").PSIsContainer"')
            if attr then
                local result = attr:read("*l")
                attr:close()
                isDir = (result and result:lower():find("true")) and true or false
            else
                isDir = false
            end
        else
            local check = io.popen('[ -d "' .. fullPath .. '" ] && echo yes || echo no')
            if check then
                local result = check:read("*l")
                check:close()
                isDir = (result == "yes")
            else
                isDir = false
            end
        end

        local modtime = 0
        if isDesktopPlatform() then
            local mp = io.popen('forfiles /P "' .. path .. '" /M "' .. item .. '" /C "cmd /c echo @fdate @ftime"')
            if mp then
                local line = mp:read("*l")
                mp:close()
                if line then
                    local m, d, y, h, mi, s = line:match("(%d+)/(%d+)/(%d+)%s+(%d+):(%d+):(%d+)")
                    if y then modtime = os.time({year=y, month=m, day=d, hour=h, min=mi, sec=s}) end
                end
            end
        else
            local mp = io.popen('stat -c "%Y" "' .. fullPath .. '"')
            if mp then
                local line = mp:read("*l")
                mp:close()
                modtime = tonumber(line) or 0
            end
        end

        table.insert(dirItems, {
            name = item,
            type = (isDir == 0 or isDir == true) and "directory" or "file",
            path = fullPath,
            modtime = modtime
        })
    end
    p:close()

    table.sort(dirItems, function(a, b)
        if a.type ~= b.type then
            return a.type == "directory"
        end
        if a.type == "directory" then
            return a.modtime > b.modtime
        end
        -- files: MISSING.txt always last
        local aIsMissing = (a.name:upper():find("MISSING") ~= nil)
        local bIsMissing = (b.name:upper():find("MISSING") ~= nil)
        if aIsMissing ~= bIsMissing then
            return not aIsMissing  -- non-missing comes first
        end
        return false  -- preserve order otherwise
    end)

    for i, item in ipairs(dirItems) do
        item.index = i
    end
end

function getSavesRootPath()
    if isAndroidPlatform() then
        return "The Interpreter" -- Use a subfolder
    else
        local sep = (isDesktopPlatform()) and "\\" or "/"
        return getDesktopPath() .. sep .. "DEMON INTERPRETER FILES"
    end
end

function removeLoveDir(path)
    local items = love.filesystem.getDirectoryItems(path)
    for _, item in ipairs(items) do
        local full = path .. "/" .. item
        local info = love.filesystem.getInfo(full)
        if info then
            if info.type == "directory" then removeLoveDir(full)
            else love.filesystem.remove(full) end
        end
    end
    love.filesystem.remove(path)
end

function loadDirectory(path)
    currentPath = path
    dirItems = {}
    savesScrollY = 0
    local items = love.filesystem.getDirectoryItems(path)
    for _, item in ipairs(items) do
        local fullPath = (path == "") and item or (path .. "/" .. item)
        local info = love.filesystem.getInfo(fullPath)
        if info then
            table.insert(dirItems, {name = item, type = info.type, path = fullPath})
        end
    end
    table.sort(dirItems, function(a, b)
        if a.type == b.type then return a.name < b.name end
        return a.type == "directory"
    end)

    for i, item in ipairs(dirItems) do
        item.index = i
    end
end

function deleteItem(item)

    if isAndroidPlatform() then
        if item.type == "directory" then removeLoveDir(item.path)
        else love.filesystem.remove(item.path) end
        loadDirectory(currentPath)
    else
        local sep = isDesktopPlatform() and "\\" or "/"
        local cmd
        if item.type == "directory" then
            cmd = isDesktopPlatform() and ('rmdir /s /q "' .. item.path .. '"') or ('rm -rf "' .. item.path .. '"')
        else
            cmd = isDesktopPlatform() and ('del /f /q "' .. item.path .. '"') or ('rm -f "' .. item.path .. '"')
        end
        os.execute(cmd)
        loadRealDirectory(currentPath)
    end
end

function openDeleteConfirm(item)
    local dw, dh = math.min(sw * 0.8, 320), 140
    deleteConfirm = {
        item = item,
        x = (sw - dw) / 2,
        y = (sh - dh) / 2,
        w = dw,
        h = dh
    }
end

function saveProcess()

    local inputFileName, outputFileName
    if mode == "interpret" then
        entryName = "NORMAL"
        inputFileName = "input(DEMON).txt"
        outputFileName = "output(NORMAL).txt"
    else
        entryName = "DEMON"
        inputFileName = "input(NORMAL).txt"
        outputFileName = "output(DEMON).txt"
    end

    local subFolderName = entryName .. os.date("%Y-%m-%d_%H-%M-%S")


    local missingChars = {}
    local missingCount = 0
    for char in rawText:gmatch("[%z\1-\127\194-\244][\128-\191]*") do
        if not eldaMap[char] and not char:match("[%w%p%s]") then
            if not missingChars[char] then missingChars[char] = true; missingCount = missingCount + 1 end
        end
    end

    local alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    local assigned = {}
    for _, v in pairs(eldaMap) do assigned[v] = true end
    local unusedAlpha = ""
    for i = 1, #alphabet do
        local letter = alphabet:sub(i,i)
        if not assigned[letter] then unusedAlpha = unusedAlpha .. letter .. " " end
    end

    local missingFileContent = "--- UNKNOWN SYMBOLS IN INPUT ---\n"
    if missingCount == 0 then
        missingFileContent = missingFileContent .. "None found.\n"
    else
        for c, _ in pairs(missingChars) do missingFileContent = missingFileContent .. c .. "  " end
        missingFileContent = missingFileContent .. "\n"
    end
    missingFileContent = missingFileContent .. "\n--- ALPHABETS NOT YET ASSIGNED ---\n"
    missingFileContent = missingFileContent .. (unusedAlpha == "" and "All assigned!" or unusedAlpha)

    -- Saving Logic
    local root = getSavesRootPath()
    if not love.filesystem.getInfo(root) then
        love.filesystem.createDirectory(root)
    end

    local savePath = root .. "/" .. subFolderName -- "The Interpreter/NORMAL2026..."

    love.filesystem.createDirectory(root) -- Ensure "The Interpreter" exists
    love.filesystem.createDirectory(savePath) -- Ensure the subfolder exists

    -- Use savePath so they go INTO the subfolder
    love.filesystem.write(savePath .. "/" .. inputFileName, rawText)
    love.filesystem.write(savePath .. "/" .. outputFileName, decodedText)
    love.filesystem.write(savePath .. "/MISSING.txt", missingFileContent)

    if isAndroidPlatform() then
        statusMsg = "SAVED TO INTERNAL STORAGE"
    else
        local sep = isDesktopPlatform() and "\\" or "/"
        local masterDir = getSavesRootPath()
        local sessionDir = masterDir .. sep .. subFolderName

        os.execute((isDesktopPlatform()) and ('mkdir "' .. masterDir .. '" 2>nul & mkdir "' .. sessionDir .. '" 2>nul') or ('mkdir -p "' .. sessionDir .. '"'))

        local fRaw = io.open(sessionDir .. sep .. inputFileName, "w")
        if fRaw then fRaw:write(rawText); fRaw:close() end

        local fDec = io.open(sessionDir .. sep .. outputFileName, "w")
        if fDec then fDec:write(decodedText); fDec:close() end

        local fMiss = io.open(sessionDir .. sep .. "MISSING.txt", "w")
        if fMiss then fMiss:write(missingFileContent); fMiss:close() end

        statusMsg = "SAVED TO DESKTOP AND APP STORAGE"
    end

    statusColor = {0.4, 1, 1}
    statusTimer = 5.0
end

function love.load()

    if isAndroidPlatform() then
        love.window.setMode(360, 806, { fullscreen = true, resizable = false })
    else
        love.window.setMode(360, 666, {resizable=true})
    end

    setupUI()

    Keymap.init(eldaMaps)   -- built-in tables + the user's saved edits
    Settings.init({
        languages = languages,
        getLang = function() return currentLang end,
        setLang = function(i)
            currentLang = i
            findEldaMap()
            updateTextWrapping()
        end,
        isAndroid = isAndroidPlatform,
        tabH = 40,
        deactivateKeyboard = function()
            inputActive = false
            inputBox.active = false
            outputBox.active = false
            Keyboard.setActive(false)
        end,
    })
    findEldaMap()

    local rootPath = getSavesRootPath()
    print("LOAD DIRECTORY SKIPPED TEST")
    if isAndroidPlatform() then loadDirectory(rootPath)
    else loadRealDirectory(rootPath) end
    print("LOAD DIRECTORY SKIPPED TEST")

    love.graphics.setFont(font)

    love.window.setTitle("DEMON INTERPRETER - ELDA DUAL")
    love.keyboard.setKeyRepeat(true)

    sw, sh = love.graphics.getDimensions()
    mx, my = love.mouse.getPosition()

    Keyboard.setActive(false)

end

function love.update(dt)

    sw, sh = love.graphics.getDimensions()
    mx, my = love.mouse.getPosition()

    if statusTimer > 0 then statusTimer = statusTimer - dt end

    if toastFadeTimer > 0 then
        toastFadeTimer = toastFadeTimer - dt
    end

    if toastFadeTimer <= 0 then
        toastFadeMsg = nil
    end

    findEldaMap()

    if currentTab == "settings" then Settings.update(dt) end

    if isAndroidPlatform() then
        Keyboard.update(dt)

    end
end

function getTextIndexFromPosition(box, lines, x, y)

    local font = love.graphics.getFont()

    local relativeY = y - (box.y + padding + box.scrollY)

    local lineIndex = math.floor(relativeY / lineH) + 1

    if lineIndex < 1 or lineIndex > #lines then
        return nil
    end

    local lineText = lines[lineIndex] or ""

    local relativeX = x - (box.x + padding)

    local len = utf8.len(lineText) or 0

    -- ⭐ Build cumulative width array
    local offsets = {}
    offsets[0] = 0

    local prefix = ""

    for i = 1, len do
        local ch = utf8_sub(lineText, i, i)
        prefix = prefix .. ch
        offsets[i] = font:getWidth(prefix)
    end

    -- ⭐ Find nearest character boundary (stable snapping)
    local bestIndex = 0
    local bestDist = math.huge

    for i = 0, len do
        local dist = math.abs(relativeX - offsets[i])
        if dist < bestDist then
            bestDist = dist
            bestIndex = i
        end
    end

    return {
        line = lineIndex,
        char = bestIndex
    }
end

function getCaretPixelPosition(box, lines, caretIndex)

    local font = love.graphics.getFont()

    local index = 0
    local line = #lines
    local char = utf8.len(lines[#lines] or "")

    for i = 1, #lines do

        local len = utf8.len(lines[i]) or 0

        if caretIndex <= index + len then
            line = i
            char = caretIndex - index
            break
        end

        index = index + len
    end

    local text = lines[line] or ""
    local before = utf8_sub(text, 1, char)

    local x = box.x + padding + font:getWidth(before)
    local y = box.y + padding + (line - 1) * lineH + box.scrollY

    return x, y
end

function isSelectionForward()
    -- Returns true if drag went left→right or top→bottom
    if not (selectionStart and selectionEnd) then return true end
    if selectionEnd.line > selectionStart.line then return true end
    if selectionEnd.line < selectionStart.line then return false end
    return selectionEnd.char >= selectionStart.char
end

function moveCaretToSelectionEnd()

    if not (selectionStart and selectionEnd) then return end

    local lines =
        (selectionBox == inputBox) and wrappedInput or wrappedOutput

    if not lines then return end

    -- ⭐ use REAL drag end, not normalized end
    local e = selectionEnd

    local globalIndex = 0

    for line = 1, e.line - 1 do
        globalIndex = globalIndex + utf8.len(lines[line] or "")
    end

    -- Forward drag (left→right / top→bottom): use e.char
    -- Reverse drag (right→left / bottom→top): use e.char - 1
    if isSelectionForward() then
        globalIndex = globalIndex + e.char
    else
        globalIndex = globalIndex + e.char - 1
    end

    caretIndex = globalIndex
    updateTextInputCaretViewport()
end

function selectionToRawIndices(s, e, lines, sourceText)
    -- Walk rawText char by char, tracking which wrapped line/char we're on.
    -- This avoids trusting wrapped line lengths (which have phantom spaces).
    local rawLen = utf8.len(sourceText) or 0
    local curLine = 1
    local curChar = 0  -- char position within current wrapped line

    local globalStart = nil
    local globalEnd = nil

    local function checkPos(rawIdx)
        if not globalStart and (curLine > s.line or (curLine == s.line and curChar >= s.char)) then
            globalStart = rawIdx - 1
        end
        if not globalEnd and (curLine > e.line or (curLine == e.line and curChar >= e.char)) then
            globalEnd = rawIdx - 1
        end
    end

    for i = 1, rawLen do
        checkPos(i)
        if globalStart and globalEnd then break end

        local lineText = lines[curLine] or ""
        local lineLen = utf8.len(lineText) or 0

        curChar = curChar + 1

        -- advance line if we've passed the visible chars of this wrapped line
        if curChar >= lineLen and curLine < #lines then
            curLine = curLine + 1
            curChar = 0
        end
    end

    -- handle end-of-text edge case
    if not globalStart then globalStart = rawLen end
    if not globalEnd then globalEnd = rawLen end

    return globalStart, globalEnd
end

function deleteSelection()
    if not (selectionStart and selectionEnd) then return false end

    local s, e = normalizeSelection(selectionStart, selectionEnd)
    local lines = (selectionBox == inputBox) and wrappedInput or wrappedOutput
    if not lines then return false end

    local globalStart, globalEnd = selectionToRawIndices(s, e, lines, rawText)

    local byteStart = utf8.offset(rawText, globalStart + 1)
    local byteEnd   = utf8.offset(rawText, globalEnd + 1)

    if byteStart and byteEnd then
        rawText = rawText:sub(1, byteStart - 1) .. rawText:sub(byteEnd)
        caretIndex = globalStart
        updateTextInputCaretViewport()
    end

    selectionStart = nil
    selectionEnd = nil
    updateTextWrapping()
    return true
end

function updateTextInputCaretViewport()
    if currentTab ~= "interpreter" then return end
    if not inputBox.active then return end

    local lines =
        (inputBox == selectionBox) and wrappedInput or wrappedOutput

    if not lines then return end

    local cx, cy = getCaretPixelPosition(inputBox, wrappedInput, caretIndex)

    if love.keyboard.setTextInput and inputBox.active then
        love.keyboard.setTextInput(true)
    end

    -- ⭐ Move viewport logically (if mobile platform)
    if isAndroidPlatform() then
        love.graphics.origin()
    end
end

function openSelectionToast(box, line, char)

    if not box then return end

    local lines =
        (box == inputBox) and wrappedInput or wrappedOutput

    if not lines then return end

    -- convert selection → global index
    local globalIndex = 0
    for i = 1, line - 1 do
        globalIndex = globalIndex + utf8.len(lines[i] or "")
    end
    globalIndex = globalIndex + char

    -- caret pixel position
    local cx, cy = getCaretPixelPosition(box, lines, globalIndex)

    local font = love.graphics.getFont()
    local padding_inner = 12
    local copyW = font:getWidth("COPY") + padding_inner * 2
    local copyAllW = font:getWidth("COPY ALL") + padding_inner * 2
    local toastW = copyW + copyAllW
    local toastH = 32
    local margin = 10

    local px = cx - toastW / 2

    local caretHeight = 18
    local safetyMargin = 6

    -- Define forbidden overlap zone
    local zoneTop = cy
    local zoneBottom = cy + caretHeight + safetyMargin

    local py

    local spaceAboveZone = zoneTop - box.y
    local spaceBelowZone = (box.y + box.h) - zoneBottom

    -- ⭐ If enough clean space ABOVE caret zone → place toast above
    if spaceAboveZone >= toastH + margin then
        py = zoneTop - toastH - margin

    -- ⭐ Otherwise if enough clean space BELOW caret zone → place toast below
    elseif spaceBelowZone >= toastH + margin then
        py = zoneBottom + margin

    -- ⭐ Emergency fallback (rare edge case)
    else
        py = math.max(
            box.y + 4,
            math.min(
                box.y + box.h - toastH - 4,
                cy - toastH / 2
            )
        )
    end

    py = math.max(box.y + 2, math.min(box.y + box.h - toastH - 2, py))

    -- ===== Horizontal smart placement =====

    -- too far left
    if px < box.x then
        px = box.x + 4
    end

    -- too far right
    if px + toastW > box.x + box.w - SCROLLBAR_TRACK_W * 2 then
        px = box.x + box.w - SCROLLBAR_TRACK_W * 2 - toastW
    end

    selectionActionToast = {
        x = px,
        y = py,
        w = toastW,
        h = toastH,
        box = box
    }
end

function openToastSelectionActionToast(line, char)
    if not toastPopup or not toastSelectionBox then return end

    local lines = toastPopup.lines
    local box = toastSelectionBox
    local font = love.graphics.getFont()

    -- ⭐ Size bar to fit text instead of hardcoded 120
    local padding_inner = 12
    local copyW = font:getWidth("COPY") + padding_inner * 2
    local copyAllW = font:getWidth("COPY ALL") + padding_inner * 2
    local toastW = copyW + copyAllW
    local toastH = 32

    local globalIndex = 0
    for i = 1, line - 1 do
        globalIndex = globalIndex + utf8.len(lines[i] or "")
    end
    globalIndex = globalIndex + char

    local cx, cy = getCaretPixelPosition(box, lines, globalIndex)
    local margin = 10
    local px = cx - toastW / 2

    local zoneTop = cy
    local zoneBottom = cy + 18 + 6
    local py

    local spaceAbove = zoneTop - box.y
    local spaceBelow = (box.y + box.h) - zoneBottom

    if spaceAbove >= toastH + margin then
        py = zoneTop - toastH - margin
    elseif spaceBelow >= toastH + margin then
        py = zoneBottom + margin
    else
        py = math.max(box.y + 4, math.min(box.y + box.h - toastH - 4, cy - toastH / 2))
    end

    local contentW = toastPopup.w - SCROLLBAR_TRACK_W
    local boxLeft = toastPopup.x

    if px < boxLeft then px = boxLeft + 4 end
    local effectiveW = toastPopup.w - SCROLLBAR_TRACK_W * 2
    if px + toastW > toastPopup.x + effectiveW then px = toastPopup.x + effectiveW - toastW end

    py = math.max(box.y + 2, math.min(box.y + box.h - toastH - 2, py))
    toastSelectionActionToast = { x = px, y = py, w = toastW, h = toastH }
end

function copySelection()

    showFadeMessage("COPY CALLED")  -- ⭐ before everything
    if not (selectionStart and selectionEnd) then
        showFadeMessage("SELECTION NIL")
        return
    end

    if not (selectionStart and selectionEnd) then return end

    local s, e = normalizeSelection(selectionStart, selectionEnd)

    local sourceText = (selectionBox == outputBox) and decodedText or rawText
    local lines = (selectionBox == inputBox) and wrappedInput or wrappedOutput

    local globalStart, globalEnd = selectionToRawIndices(s, e, lines, sourceText)

    if not globalStart or not globalEnd then return end

    local byteStart = utf8.offset(sourceText, globalStart + 1)
    local byteEnd   = utf8.offset(sourceText, globalEnd + 1)

    -- ⭐ Show debug info instead of copying
    showFadeMessage(
        "s=" .. s.line .. "," .. s.char ..
        " e=" .. e.line .. "," .. e.char ..
        "\ngS=" .. tostring(globalStart) ..
        " gE=" .. tostring(globalEnd) ..
        "\nbS=" .. tostring(byteStart) ..
        " bE=" .. tostring(byteEnd)
    )
    if byteStart and byteEnd then
        love.system.setClipboardText(sourceText:sub(byteStart, byteEnd - 1))
    end

    selectionStart = nil
    selectionEnd = nil
end

function copyAll()
    local textToCopy = (selectionBox == outputBox) and decodedText or rawText
    love.system.setClipboardText(textToCopy)
    selectionStart = nil
    selectionEnd = nil
end

function collapseSelectionToCaret()
    if selectionStart and selectionEnd then
        moveCaretToSelectionEnd()

        selectionStart = nil
        selectionEnd = nil
        selectionBox = nil
        selectionActionToast = nil
    end
end

function love.resize(w, h)

    setupUI()
    updateTextWrapping()
    if toastPopup then openToast(toastPopup.title, "Resize closed to prevent wrapping issues. Please reopen.") toastPopup = nil end
end

function love.textinput(t)
    if currentTab == "settings" then Settings.textinput(t) return end
    if currentTab ~= "interpreter" or toastPopup or deleteConfirm then return end

    local bytes = ""
    for i = 1, #t do
        bytes = bytes .. string.byte(t, i) .. " "
    end
    showFadeMessage("IN: [" .. bytes .. "]")

    if selectionStart and selectionEnd then
        deleteSelection()

    end
    selectionActionToast = nil

    table.insert(historyStack, rawText)
    redoStack = {}

    if inputBox.active then

        showFadeMessage("TXT: " .. tostring(t))
        local byteIndex = utf8.offset(rawText, caretIndex + 1)

        if byteIndex then
            rawText =
                rawText:sub(1, byteIndex - 1) ..
                t ..
                rawText:sub(byteIndex)
        else
            rawText = rawText .. t
        end

        caretIndex = caretIndex + utf8.len(t)
        updateTextInputCaretViewport()

    elseif outputBox.active then
        rawText = rawText .. (encodeMap[t:lower()] or t)
    end

    updateTextWrapping()
end

function love.keypressed(key)

    if currentTab == "settings" and Settings.keypressed(key) then return end

    if currentTab == "interpreter" and not toastPopup and not deleteConfirm then

        if key == "lctrl" or key == "rctrl" then ctrlDown = true end
        if ctrlDown then

            if key == "c" then

                moveCaretToSelectionEnd()
                copySelection()
                selectionActionToast = false

            elseif key == "v" then
                local clip = love.system.getClipboardText()

                if clip then
                    -- delete selection first (sets caretIndex to correct spot)
                    if selectionStart and selectionEnd then
                        deleteSelection()
                    end
                    selectionStart = nil
                    selectionEnd = nil
                    selectionActionToast = nil

                    table.insert(historyStack, rawText)
                    redoStack = {}

                    if inputBox.active then

                        local bytePos = utf8.offset(rawText, caretIndex + 1)

                        if bytePos then
                            rawText =
                                rawText:sub(1, bytePos - 1) ..
                                clip ..
                                rawText:sub(bytePos)
                        else
                            rawText = rawText .. clip
                        end

                        caretIndex = caretIndex + utf8.len(clip)
                        updateTextInputCaretViewport()

                    elseif outputBox.active then

                        local encoded = encodeNormal(clip)

                        local bytePos = utf8.offset(rawText, caretIndex + 1)

                        if bytePos then
                            rawText =
                                rawText:sub(1, bytePos - 1) ..
                                encoded ..
                                rawText:sub(bytePos)
                        else
                            rawText = rawText .. encoded
                        end

                        caretIndex = caretIndex + utf8.len(encoded)
                        updateTextInputCaretViewport()
                    end
                end

                updateTextWrapping()
            end
        end
        if inputBox.active then

            if key == "z" and #historyStack > 0 then
                table.insert(redoStack, rawText); rawText = table.remove(historyStack)
                selectionStart = nil
                selectionEnd = nil
                selectionActionToast = false


            elseif key == "y" and #redoStack > 0 then
                table.insert(historyStack, rawText); rawText = table.remove(redoStack)
                selectionStart = nil
                selectionEnd = nil
                selectionActionToast = false

            end
            updateTextWrapping()

            if key == "backspace" then
                showFadeMessage("BACKSPACE FIRED")
                selectionActionToast = false
                if deleteSelection() then
                    collapseSelectionToCaret()
                    return
                end

                if caretIndex > 0 then
                    local startByte = utf8.offset(rawText, caretIndex)
                    local endByte = utf8.offset(rawText, caretIndex + 1)

                    rawText =
                        rawText:sub(1, startByte - 1) ..
                        rawText:sub(endByte)

                    caretIndex = caretIndex - 1
                    updateTextInputCaretViewport()
                    selectionStart = nil
                    selectionEnd = nil

                end
                updateTextWrapping()
            end

            if key == "left" then
                caretIndex = math.max(0, caretIndex - 1)
                updateTextInputCaretViewport()
                collapseSelectionToCaret()

            elseif key == "right" then
                caretIndex = math.min(utf8.len(rawText) or 0, caretIndex + 1)
                updateTextInputCaretViewport()
                collapseSelectionToCaret()

            end
        end

    end

    if isDesktopPlatform() or isAndroidPlatform() then
        if key == "escape" then
            love.event.quit()
        end
    end
end

function love.keyreleased(key)
    if key == "lctrl" or key == "rctrl" then ctrlDown = false end

end

function love.wheelmoved(dx, dy)

    if currentTab == "settings" then Settings.wheel(dy) return end

    if deleteConfirm then return end
    if toastPopup then
        local viewH = toastPopup.h - 95
        local maxScroll = math.max(0, (#toastPopup.lines * lineH + padding * 2) - viewH)
        toastPopup.scrollY = math.min(0, math.max(-maxScroll, toastPopup.scrollY + dy * 25))
    elseif currentTab == "interpreter" then
        local target = inputBox.active and inputBox or outputBox
        local lines = (target == inputBox) and wrappedInput or wrappedOutput
        local maxScroll = math.max(0, (#lines * lineH + padding * 2) - target.h)
        target.scrollY = math.min(0, math.max(-maxScroll, target.scrollY + dy * 25))
    elseif currentTab == "saves" then
        local info = getSavesScrollerInfo()
        if info and info.maxScroll > 0 then

            local scrollSpeed = 30   -- ⭐ Higher = faster wheel scroll

            savesScrollY = math.min(
                0,
                math.max(
                    -info.maxScroll,
                    savesScrollY + dy * scrollSpeed
                )
            )

        end
    end
end

function getSavesScrollerInfo()

    local pathFontH = love.graphics.getFont():getHeight()
    local listYStart = path.y + pathFontH + 15
    local itemH = 40
    local listH = sh - 40 - listYStart

    local contentH = #dirItems * itemH

    if contentH <= listH then
        return nil
    end

    local trackX = sw - SAVE_LIST_RIGHT_MARGIN * SAVE_SCROLLBAR_CENTER_X_RATIO
    local trackY = listYStart
    local trackH = listH

    local thumbH = math.max(30, (listH / contentH) * listH)

    local scrollPercent = -savesScrollY / (contentH - listH)
    scrollPercent = math.max(0, math.min(1, scrollPercent))

    local thumbY = listYStart + (listH - thumbH) * scrollPercent

    local maxScroll = math.max(0, contentH - listH)

    return {
        trackX = trackX,
        trackY = trackY,
        trackH = trackH,
        thumbH = thumbH,
        thumbY = thumbY,
        maxScroll = maxScroll
    }


end

function love.mousepressed(x, y, button)

    if button ~= 1 then return end

    -- SETTINGS tab handles its own taps / scrolling (the tab bar still falls through)
    if currentTab == "settings" and Settings.pressed(x, y) then return end

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

    if currentTab == "settings" then Settings.moved(x, y) return end

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
    if currentTab == "settings" then Settings.released(x, y) end
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

function utf8_sub(str, startChar, endChar)
    local bytes = utf8.offset(str, startChar)

    if not bytes then return "" end

    local bytesEnd

    if endChar then
        bytesEnd = utf8.offset(str, endChar + 1)
        if not bytesEnd then
            return str:sub(bytes)
        end
        return str:sub(bytes, bytesEnd - 1)
    end

    return str:sub(bytes)
end

function normalizeSelection(a, b)
    if not a or not b then return a, b end

    if a.line > b.line or (a.line == b.line and a.char > b.char) then
        return b, a
    end

    return a, b
end

function getDefaultColor(elementType)
    if elementType == "tab" then
        return {0.1, 0.1, 0.1}

    elseif elementType == "tab_active" then
        return {0.2, 0.4, 0.8}

    elseif elementType == "row_idle" then
        return {0.12, 0.12, 0.15}

    elseif elementType == "remove_btn" then
        return {0.55, 0.08, 0.08}

    elseif elementType == "clear_btn" then
        return {0.6, 0.1, 0.1}

    elseif elementType == "proceed_btn" then
        return {0.1, 0.5, 0.1}

    end

    return {0.2, 0.2, 0.2}
end

function getHoverColor(elementType)
    if elementType == "tab" then
        return {0.15, 0.2, 0.3}

    elseif elementType == "row" then
        return {0.18, 0.18, 0.22}

    elseif elementType == "remove_btn" then
        return {0.75, 0.2, 0.2}

    elseif elementType == "clear_btn" then
        return {1, 0.2, 0.2}

    elseif elementType == "proceed_btn" then
        return {0.2, 0.8, 0.2}

    end

    return {0.3, 0.3, 0.3}
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

    -- ===================== SETTINGS TAB =====================
    if currentTab == "settings" then
        Settings.draw(sw, sh, mx, my)
        love.graphics.setFont(font)
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

    local tabs = {
        { id = "interpreter", label = "INTERPRETER" },
        { id = "saves",       label = "SAVES (OS)" },
        { id = "settings",    label = "SETTINGS" },
    }
    local tabW = sw / #tabs
    for i, tab in ipairs(tabs) do
        local tx = (i - 1) * tabW
        local hover = mx >= tx and mx < tx + tabW and my >= tabY
        love.graphics.setColor(currentTab == tab.id and {0.2, 0.4, 0.8} or (hover and {0.15, 0.2, 0.3} or {0.1, 0.1, 0.1}))
        love.graphics.rectangle("fill", tx, tabY, tabW, tabH)
        love.graphics.setColor(1, 1, 1)
        love.graphics.setFont(titleFont)
        love.graphics.printf(tab.label, tx, tabY + 12, tabW, "center")
        love.graphics.setFont(font)

        local tabId = tab.id
        addHitbox(tx, tabY, tabW, tabH, function()
            if currentTab == "settings" and tabId ~= "settings" then Settings.leave() end
            currentTab = tabId
            if tabId == "interpreter" then
                local rootPath = getSavesRootPath()
                if isAndroidPlatform() then loadDirectory(rootPath) else loadRealDirectory(rootPath) end
                updateTextWrapping()   -- pick up key edits made in SETTINGS
            elseif tabId == "saves" then
                if isAndroidPlatform() then loadDirectory(currentPath) else loadRealDirectory(currentPath) end
            else
                Settings.enter()
            end
        end, 1)
    end

    love.graphics.setColor(0.3, 0.3, 0.3)
    for i = 1, #tabs - 1 do love.graphics.line(i * tabW, tabY, i * tabW, sh) end
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

    if currentTab == "interpreter" then   -- (debug overlay, interpreter tab only)
        love.graphics.print("TEXT: " .. rawText, 100, 100)
        love.graphics.print("IND: " .. caretIndex, 300, 100)
    end


    if isAndroidPlatform() then
        Keyboard.draw()
    end


end
