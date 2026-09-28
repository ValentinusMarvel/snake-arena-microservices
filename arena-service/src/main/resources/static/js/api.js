/**
 * Snake Arena: Clash of Serpents
 * Module: REST API Client & Terminal Telemetry
 * --------------------------------------------------------------------------
 * Handles communication with Player Service (8081) and Arena Service (8082).
 * Includes:
 * - Roundtrip latency calculation with performance.now()
 * - Visual button feedback (in-flight loading pulse, success/error flash)
 * - Real-time terminal feed with color-coded HTTP verbs and latency badges
 * - Full Lobby & In-Game CRUD operations with dual-versioning (v1 / v2)
 */

window.SnakeArena = window.SnakeArena || {};

(function() {
    const currentHost = window.location.hostname || "localhost";
    const PLAYER_SERVICE_URL = `http://${currentHost}:8081`;
    const ARENA_SERVICE_URL = `http://${currentHost}:8082`;

    const linkPlayer = document.getElementById("linkSwaggerPlayer");
    if (linkPlayer) linkPlayer.href = `${PLAYER_SERVICE_URL}/swagger-ui.html`;
    const linkArena = document.getElementById("linkSwaggerArena");
    if (linkArena) linkArena.href = `${ARENA_SERVICE_URL}/swagger-ui.html`;

    let currentVersion = "v1";
    let registeredPlayerId = null;
    let nameLookupTimer = null;

    /**
     * Log an API request/response into the live terminal feed
     * @param {string} method - GET, POST, PUT, PATCH, DELETE, INFO
     * @param {string} endpoint - Target URI
     * @param {number} status - HTTP status code
     * @param {object} reqBody - Request payload
     * @param {object} resBody - Response payload
     * @param {number|null} durationMs - Latency duration in ms
     */
    function logApi(method, endpoint, status, reqBody, resBody, durationMs = null) {
        const container = document.getElementById("apiLogs");
        if (!container) return;

        const entry = document.createElement("div");
        entry.className = "log-entry";

        const tagClass = "tag-" + method.toLowerCase();
        const statusClass = "status-" + status;

        let reqStr = reqBody ? `REQ: ${JSON.stringify(reqBody, null, 2)}\n` : '';
        let resStr = resBody ? `RES: ${JSON.stringify(resBody, null, 2)}` : '';

        // Latency Badge formatting
        let latencyBadge = '';
        if (durationMs !== null && durationMs !== undefined) {
            const tier = durationMs < 50 ? 'latency-fast' : durationMs < 200 ? 'latency-med' : 'latency-slow';
            latencyBadge = `<span class="log-latency ${tier}">[${durationMs}ms]</span>`;
        }

        entry.innerHTML = `
            <div class="log-header">
                <div style="display:flex; align-items:center; gap:6px;">
                    <span class="method-tag ${tagClass}">${method}</span>
                    ${latencyBadge}
                </div>
                <span class="log-endpoint">${endpoint}</span>
                <span class="log-status ${statusClass}">${status}</span>
            </div>
            ${reqStr || resStr ? `<pre class="log-json">${reqStr}${resStr}</pre>` : ''}
        `;

        container.insertBefore(entry, container.firstChild);
    }

    function clearLogs() {
        const container = document.getElementById("apiLogs");
        if (container) container.innerHTML = "";
    }

    /**
     * Unified HTTP caller with latency measurement and tactile button feedback
     */
    async function makeApiCall(method, url, body = null, silent = false, triggerBtn = null) {
        const startTime = performance.now();

        // Apply loading state to trigger button if supplied
        if (triggerBtn && triggerBtn.classList) {
            triggerBtn.classList.add("btn-loading");
        }

        try {
            const options = {
                method: method,
                headers: { "Content-Type": "application/json" }
            };
            if (body) options.body = JSON.stringify(body);

            const response = await fetch(url, options);
            const duration = Math.round(performance.now() - startTime);

            let data = null;
            const contentType = response.headers.get("content-type");
            if (contentType && contentType.includes("application/json")) {
                data = await response.json();
            } else {
                data = { message: response.statusText };
            }

            if (!silent) {
                const cleanEndpoint = url.replace(/^http:\/\/[^/]+/, "");
                logApi(method, cleanEndpoint, response.status, body, data, duration);
            }

            // Visual feedback flash on triggering button
            if (triggerBtn && triggerBtn.classList) {
                triggerBtn.classList.remove("btn-loading");
                const flashClass = response.ok ? "btn-success-flash" : "btn-error-flash";
                triggerBtn.classList.add(flashClass);
                setTimeout(() => triggerBtn.classList.remove(flashClass), 400);
            }

            return { success: response.ok, status: response.status, data: data, duration: duration };
        } catch (err) {
            const duration = Math.round(performance.now() - startTime);
            if (!silent) {
                const cleanEndpoint = url.replace(/^http:\/\/[^/]+/, "");
                logApi(method, cleanEndpoint, 500, body, { error: err.message }, duration);
            }
            if (triggerBtn && triggerBtn.classList) {
                triggerBtn.classList.remove("btn-loading");
                triggerBtn.classList.add("btn-error-flash");
                setTimeout(() => triggerBtn.classList.remove("btn-error-flash"), 400);
            }
            return { success: false, status: 500, data: { error: err.message }, duration: duration };
        }
    }

    function switchVersion(version) {
        if (window.playSfx) window.playSfx('click');
        currentVersion = version;
        const btnV1 = document.getElementById("btnV1");
        const btnV2 = document.getElementById("btnV2");
        const verLabel = document.getElementById("currentVerLabel");
        if (btnV1) btnV1.classList.toggle("active", version === "v1");
        if (btnV2) btnV2.classList.toggle("active", version === "v2");
        if (verLabel) verLabel.innerText = version === "v1" ? "v1 (Flat DTO)" : "v2 (ClassProfile)";
    }

    async function triggerBreakingChangeTest() {
        if (window.playSfx) window.playSfx('click');
        const v1Payload = {
            username: window.playerName || "PilotTest",
            snakeType: window.selectedClass || "VIPER",
            speed: 14
        };
        const endpoint = `${PLAYER_SERVICE_URL}/api/v2/players`;
        const btn = document.querySelector(".btn-test-break");
        await makeApiCall("POST", endpoint, v1Payload, false, btn);
    }

    function updatePlayerIdBadge(id) {
        registeredPlayerId = id;
        const badge = document.getElementById("registeredIdBadge");
        if (badge) {
            badge.innerText = id ? id : "Nomor ID";
            badge.style.color = id ? "var(--tactical-emerald)" : "var(--text-dim)";
        }
    }

    function getSafeUsername(base, suffix = "") {
        let name = (base && base.trim()) ? base.trim() : "Pilot";
        name = name.replace(/[^a-zA-Z0-9_]/g, "");
        const maxBase = 20 - suffix.length;
        if (name.length > maxBase) {
            name = name.substring(0, maxBase);
        }
        if (name.length < 3) {
            name = (name + "Ace").substring(0, maxBase);
        }
        return (name + suffix).substring(0, 20);
    }

    function buildV2ClassProfile(className) {
        const cls = (className || "VIPER").toUpperCase();
        if (cls === "PYTHON") {
            return {
                className: "PYTHON",
                passiveEffect: "THICK_BODY",
                activeAbility: {
                    name: "Apple Magnet",
                    cooldownSeconds: 7,
                    speedMultiplier: 1.3
                }
            };
        } else if (cls === "COBRA") {
            return {
                className: "COBRA",
                passiveEffect: "GOLDEN_SENSE",
                activeAbility: {
                    name: "Poison Spit",
                    cooldownSeconds: 6,
                    speedMultiplier: 1.5
                }
            };
        } else {
            return {
                className: "VIPER",
                passiveEffect: "POISON_TRAIL",
                activeAbility: {
                    name: "Venom Dash",
                    cooldownSeconds: 5,
                    speedMultiplier: 1.8
                }
            };
        }
    }

    function lookupPlayerByName(name) {
        if (nameLookupTimer) clearTimeout(nameLookupTimer);
        const clean = (name || "").trim();
        if (!clean) {
            updatePlayerIdBadge(null);
            return;
        }
        nameLookupTimer = setTimeout(async () => {
            try {
                let lookupName = clean;
                if (!lookupName.includes("#") && window.SnakeArena && window.SnakeArena.App && window.SnakeArena.App.getDeviceProfile) {
                    const devProfile = window.SnakeArena.App.getDeviceProfile(clean);
                    if (devProfile && devProfile.tag) {
                        lookupName = `${clean}#${devProfile.tag}`;
                    }
                }
                const res = await fetch(`${PLAYER_SERVICE_URL}/api/v1/players`);
                if (res.ok) {
                    const list = await res.json();
                    if (Array.isArray(list)) {
                        const found = list.find(p => p.username && (p.username.toLowerCase() === lookupName.toLowerCase() || p.username.toLowerCase() === clean.toLowerCase()));
                        if (found && found.id) {
                            updatePlayerIdBadge(found.id);
                            if (window.SnakeArena && window.SnakeArena.App) {
                                window.SnakeArena.App.setPlayerIdentity(found.id, found.username);
                            } else {
                                window.playerId = found.id;
                                window.playerName = found.username;
                            }
                            const hudP = document.getElementById("hudPlayer");
                            if (hudP) {
                                hudP.innerText = found.username;
                                hudP.style.color = "#ffffff";
                            }
                            return;
                        }
                    }
                }
                updatePlayerIdBadge(null);
            } catch(e) {}
        }, 300);
    }

    // 1. POST: Register Player in Player Service (Lobby) OR Deploy / Sync Ular (In-Game)
    async function apiCreatePlayer() {
        if (window.playSfx) window.playSfx('click');
        const btn = document.getElementById("btnCreatePlayer");

        // In-Game POST: Sync active snake without resetting position or score
        if (window.gameRunning && window.snakeId) {
            const currentCallsign = window.playerName || "Serpent";
            const joinPayload = {
                playerId: registeredPlayerId || window.playerId,
                playerName: currentCallsign,
                snakeType: window.selectedClass || "VIPER",
                color: window.selectedColor || "#059669"
            };
            const arenaRes = await makeApiCall("POST", `${ARENA_SERVICE_URL}/api/v1/arena/snakes`, joinPayload, false, btn);
            if (arenaRes.success && arenaRes.data) {
                const newSnakeId = arenaRes.data.id;
                window.snakeId = newSnakeId;
                sessionStorage.setItem("activeSnakeId", newSnakeId);
                if (window.SnakeArena && window.SnakeArena.App && window.SnakeArena.App.setSnakeId) {
                    window.SnakeArena.App.setSnakeId(newSnakeId);
                } else if (window.setSnakeId) {
                    window.setSnakeId(newSnakeId);
                }
                window.playerName = arenaRes.data.playerName;
                const hudP = document.getElementById("hudPlayer");
                if (hudP) hudP.innerText = window.playerName;
                const inp1 = document.getElementById("inputPlayerName");
                const inp2 = document.getElementById("overlayPlayerName");
                const cleanBase = window.playerName.replace(/#\d+$/, "");
                if (inp1) inp1.value = cleanBase;
                if (inp2) inp2.value = cleanBase;
                if (window.SnakeArena && window.SnakeArena.App && window.SnakeArena.App.updateTagBadges) {
                    window.SnakeArena.App.updateTagBadges(cleanBase);
                }
                await fetchArenaState();
                logApi("INFO", "/arena/snake-synced", 200, null, {
                    message: `Ular '${currentCallsign}' berhasil disinkronkan di arena tanpa mengubah posisi.`,
                    snakeId: window.snakeId
                });
            }
            return arenaRes;
        }

        // Lobby POST: Register new account in Player Service with unique tag
        const inpOverlay = document.getElementById("overlayPlayerName");
        const inpSidebar = document.getElementById("inputPlayerName");
        let rawName = (inpOverlay ? inpOverlay.value.trim() : "") || 
                      (inpSidebar ? inpSidebar.value.trim() : "") || 
                      window.playerName || 
                      ("Pilot_" + Math.floor(100 + Math.random() * 900));

        let cleanBase = rawName.replace(/#\d+$/, "").trim();
        let tag = rawName.match(/#(\d{4})$/) ? rawName.match(/#(\d{4})$/)[1] : null;
        if (!tag && window.SnakeArena && window.SnakeArena.App && window.SnakeArena.App.getDeviceProfile) {
            tag = window.SnakeArena.App.getDeviceProfile(cleanBase).tag;
        }
        let taggedName = tag ? `${cleanBase}#${tag}` : rawName;

        let res;
        const currentClass = window.selectedClass || "VIPER";
        if (currentVersion === "v1") {
            const payload = { username: taggedName, snakeType: currentClass };
            res = await makeApiCall("POST", `${PLAYER_SERVICE_URL}/api/v1/players`, payload, false, btn);
            if (!res.success && res.status === 400) {
                // If tag collided, generate fresh random tag
                tag = String(Math.floor(1000 + Math.random() * 9000));
                taggedName = `${cleanBase}#${tag}`;
                res = await makeApiCall("POST", `${PLAYER_SERVICE_URL}/api/v1/players`, { username: taggedName, snakeType: currentClass }, false, btn);
            }
        } else {
            const buildV2Payload = (uname) => ({
                username: uname,
                classProfile: buildV2ClassProfile(currentClass)
            });
            res = await makeApiCall("POST", `${PLAYER_SERVICE_URL}/api/v2/players`, buildV2Payload(taggedName), false, btn);
            if (!res.success && res.status === 400) {
                tag = String(Math.floor(1000 + Math.random() * 9000));
                taggedName = `${cleanBase}#${tag}`;
                res = await makeApiCall("POST", `${PLAYER_SERVICE_URL}/api/v2/players`, buildV2Payload(taggedName), false, btn);
            }
        }

        if (res.success && res.data) {
            const savedUname = res.data.username || taggedName;
            if (window.SnakeArena && window.SnakeArena.App) {
                window.SnakeArena.App.setPlayerIdentity(res.data.id, savedUname);
                if (window.SnakeArena.App.saveDeviceProfile && tag) {
                    window.SnakeArena.App.saveDeviceProfile(cleanBase, tag, res.data.id);
                }
                if (window.SnakeArena.App.updateTagBadges) {
                    window.SnakeArena.App.updateTagBadges(cleanBase);
                }
            } else {
                updatePlayerIdBadge(res.data.id);
                window.playerId = res.data.id;
                window.playerName = savedUname;
            }
            if (inpSidebar) inpSidebar.value = cleanBase;
            if (inpOverlay) inpOverlay.value = cleanBase;
            const hudP = document.getElementById("hudPlayer");
            if (hudP) {
                hudP.innerText = savedUname;
                hudP.style.color = "#ffffff";
            }
        }
        return res;
    }

    // 2. GET: Read Player Profile (Lobby) OR Live Snake Telemetry (In-Game)
    async function apiFetchPlayer() {
        if (window.playSfx) window.playSfx('click');
        const btn = document.getElementById("btnFetchPlayer");

        // In-Game Mode: Direct Snake Telemetry query
        if (window.gameRunning) {
            const targetSnakeId = window.snakeId || sessionStorage.getItem("activeSnakeId");
            if (targetSnakeId) {
                const res = await makeApiCall("GET", `${ARENA_SERVICE_URL}/api/v1/arena/snakes/${targetSnakeId}`, null, false, btn);
                if (res.success && res.data) {
                    const s = res.data;
                    logApi("INFO", `/arena/snakes/${targetSnakeId}/telemetry`, 200, null, {
                        snakeId: s.id,
                        callsign: s.playerName,
                        status: s.alive ? "ACTIVE_COMBAT" : "NEUTRALIZED",
                        coordinates: `[${s.x}, ${s.y}]`,
                        heading: s.direction,
                        length: s.body ? s.body.length : 0,
                        score: s.score,
                        tacticalClass: s.snakeType
                    });
                }
                return res;
            } else {
                return await makeApiCall("GET", `${ARENA_SERVICE_URL}/api/v1/arena/state`, null, false, btn);
            }
        }

        // Lobby Mode: Fetch Player Profile
        const targetId = registeredPlayerId || window.playerId;
        const endpoint = `${PLAYER_SERVICE_URL}/api/${currentVersion}/players/${targetId}`;
        const res = await makeApiCall("GET", endpoint, null, false, btn);

        if (!res.success && res.status === 404) {
            // If not found by ID, query player list to find match by username
            const listRes = await makeApiCall("GET", `${PLAYER_SERVICE_URL}/api/${currentVersion}/players`, null, true);
            if (listRes.success && Array.isArray(listRes.data) && listRes.data.length > 0) {
                const nameMatch = listRes.data.find(p => p.username === window.playerName);
                const fallbackPlayer = nameMatch || listRes.data[listRes.data.length - 1];
                updatePlayerIdBadge(fallbackPlayer.id);
                if (window.SnakeArena && window.SnakeArena.App) {
                    window.SnakeArena.App.setPlayerIdentity(fallbackPlayer.id, fallbackPlayer.username);
                } else {
                    window.playerId = fallbackPlayer.id;
                    window.playerName = fallbackPlayer.username;
                }
                const inpS = document.getElementById("inputPlayerName");
                const inpO = document.getElementById("overlayPlayerName");
                if (inpS) inpS.value = fallbackPlayer.username;
                if (inpO) inpO.value = fallbackPlayer.username;
                return await makeApiCall("GET", `${PLAYER_SERVICE_URL}/api/${currentVersion}/players/${fallbackPlayer.id}`, null, false, btn);
            }
        } else if (res.success && res.data) {
            updatePlayerIdBadge(res.data.id);
            if (window.SnakeArena && window.SnakeArena.App) {
                window.SnakeArena.App.setPlayerIdentity(res.data.id, res.data.username);
            } else {
                window.playerId = res.data.id;
                window.playerName = res.data.username;
            }
        }
        return res;
    }

    // 3. PUT: Update Player Profile (Lobby) OR Live Serpent Skin / Loadout (In-Game)
    async function apiUpdatePlayerProfile() {
        if (window.playSfx) window.playSfx('click');
        const btn = document.getElementById("btnUpdateLoadout");

        if (window.gameRunning && window.snakeId) {
            // In-Game PUT: Update live serpent skin/loadout in arena
            const classes = ["VIPER", "PYTHON", "COBRA"];
            const colors = { VIPER: "#059669", PYTHON: "#0284c7", COBRA: "#d97706" };
            const currentCls = (window.selectedClass || "VIPER").toUpperCase();
            const nextClsIdx = (classes.indexOf(currentCls) + 1) % classes.length;
            const nextClass = classes[nextClsIdx];
            const nextColor = colors[nextClass];

            const loadoutPayload = {
                snakeType: nextClass,
                color: nextColor
            };
            const res = await makeApiCall("PUT", `${ARENA_SERVICE_URL}/api/v1/arena/snakes/${window.snakeId}/loadout`, loadoutPayload, false, btn);
            if (res.success && res.data) {
                window.selectedClass = nextClass;
                window.selectedColor = nextColor;
                const hudC = document.getElementById("hudClass");
                if (hudC) {
                    hudC.innerText = nextClass;
                    hudC.style.color = nextColor;
                }
                if (window.arenaSnakes) {
                    const activeSnake = window.arenaSnakes.find(s => s.id === window.snakeId);
                    if (activeSnake) {
                        activeSnake.snakeType = nextClass;
                        activeSnake.color = nextColor;
                    }
                }
                logApi("INFO", `/arena/snakes/${window.snakeId}/loadout`, 200, null, {
                    message: `Loadout ular berhasil diubah live ke ${nextClass} (${nextColor}) via PUT.`,
                    snakeId: window.snakeId
                });
            }
            return res;
        }

        // Lobby PUT: Update player profile
        const targetId = registeredPlayerId || window.playerId;
        if (!targetId) {
            logApi("INFO", "/players/profile", 400, null, { error: "ID Pemain belum terdaftar. Silakan daftar akun (POST) terlebih dahulu." });
            return;
        }

        const inpS = document.getElementById("inputPlayerName");
        const currentInput = inpS ? inpS.value.trim() : "";

        // Cycle tactical class so change is visibly and functionally demonstrated
        const classes = ["VIPER", "PYTHON", "COBRA"];
        const colors = { VIPER: "#059669", PYTHON: "#0284c7", COBRA: "#d97706" };
        const currentCls = (window.selectedClass || "VIPER").toUpperCase();
        const nextClsIdx = (classes.indexOf(currentCls) + 1) % classes.length;
        const nextClass = classes[nextClsIdx];
        const nextColor = colors[nextClass];

        let updatedName;
        if (currentInput && currentInput !== window.playerName) {
            updatedName = getSafeUsername(currentInput);
        } else {
            const base = (window.playerName || "Pilot").replace(/_U\d+$/, "");
            updatedName = getSafeUsername(base, `_U${Math.floor(10 + Math.random() * 90)}`);
        }

        let payload;
        if (currentVersion === "v1") {
            payload = { username: updatedName, snakeType: nextClass };
        } else {
            payload = {
                username: updatedName,
                classProfile: buildV2ClassProfile(nextClass)
            };
        }

        const res = await makeApiCall("PUT", `${PLAYER_SERVICE_URL}/api/${currentVersion}/players/${targetId}`, payload, false, btn);
        if (res.success && res.data) {
            window.playerName = res.data.username;
            window.selectedClass = nextClass;
            window.selectedColor = nextColor;
            updatePlayerIdBadge(res.data.id);

            if (inpS) inpS.value = res.data.username;
            const inpO = document.getElementById("overlayPlayerName");
            if (inpO) inpO.value = res.data.username;
            const hudP = document.getElementById("hudPlayer");
            if (hudP) hudP.innerText = res.data.username;
            const hudC = document.getElementById("hudClass");
            if (hudC) {
                hudC.innerText = nextClass;
                hudC.style.color = nextColor;
            }

            if (window.SnakeArena && window.SnakeArena.App && window.SnakeArena.App.selectSidebarClass) {
                window.SnakeArena.App.selectSidebarClass(nextClass, nextColor, false);
            } else if (window.selectSidebarClass) {
                window.selectSidebarClass(nextClass, nextColor, false);
            }

            logApi("INFO", `/players/${targetId}/updated`, 200, null, {
                message: `Profil pemain berhasil diupdate (PUT). Tipe: ${nextClass}, Callsign: ${res.data.username}`,
                data: res.data
            });
        }
        return res;
    }

    // 4. PATCH: Eat Apple (In-Game) OR Patch Stats (Lobby)
    async function apiPartialUpdate() {
        if (window.playSfx) window.playSfx('click');
        const btn = document.getElementById("btnPatchStats");

        if (window.gameRunning && window.snakeId) {
            // In-Game PATCH: Consume apple in arena
            let targetAppleId = "APL-1";
            if (window.arenaApples && window.arenaApples.length > 0) {
                targetAppleId = window.arenaApples[0].id;
            }
            const res = await makeApiCall("PATCH", `${ARENA_SERVICE_URL}/api/v1/arena/snakes/${window.snakeId}/eat`, { appleId: targetAppleId }, false, btn);
            if (res.success && res.data) {
                if (window.playSfx) window.playSfx('apple');
                window.score = res.data.score || ((window.score || 0) + 10);
                const hudS = document.getElementById("hudScore");
                if (hudS) hudS.innerText = window.score;
                await fetchArenaState();
                logApi("INFO", `/arena/snakes/${window.snakeId}/eat`, 200, null, {
                    message: `Apel '${targetAppleId}' berhasil dimakan via PATCH. Skor: ${window.score}`,
                    bodyLength: res.data.body ? res.data.body.length : 0
                });
            }
            return res;
        }

        // Lobby PATCH: Partial update player stats (v1 and v2)
        const targetId = registeredPlayerId || window.playerId;
        if (!targetId) {
            logApi("INFO", "/players/stats", 400, null, { error: "ID Pemain belum terdaftar. Silakan daftar akun (POST) terlebih dahulu." });
            return;
        }
        const currentScore = window.score || 0;
        const newScore = currentScore + 75;
        const patchPayload = { score: newScore, matchCompleted: false };
        const res = await makeApiCall("PATCH", `${PLAYER_SERVICE_URL}/api/${currentVersion}/players/${targetId}/stats`, patchPayload, false, btn);
        if (res.success && res.data) {
            window.highScore = res.data.highScore;
            logApi("INFO", `/players/${targetId}/stats`, 200, null, {
                message: `Statistik pemain parsial berhasil diperbarui via PATCH! HighScore: ${res.data.highScore}`,
                tier: res.data.playerTier || res.data.tier || undefined
            });
        }
        return res;
    }

    // 5. DELETE: Eject from Arena (In-Game) OR Delete Account (Lobby)
    async function apiDeletePlayer() {
        if (window.playSfx) window.playSfx('click');
        const btn = document.getElementById("btnDeletePlayer");

        if (window.gameRunning && window.snakeId) {
            // In-Game DELETE: Eject serpent
            const res = await makeApiCall("DELETE", `${ARENA_SERVICE_URL}/api/v1/arena/snakes/${window.snakeId}`, null, false, btn);
            if (window.SnakeArena && window.SnakeArena.App && window.SnakeArena.App.endGame) {
                window.SnakeArena.App.endGame("Ejected by pilot (REST DELETE).");
            } else if (window.endGame) {
                window.endGame("Ejected by pilot (REST DELETE).");
            }
            return res;
        }

        // Lobby DELETE: Delete player profile
        const targetId = registeredPlayerId || window.playerId;
        const res = await makeApiCall("DELETE", `${PLAYER_SERVICE_URL}/api/${currentVersion}/players/${targetId}`, null, false, btn);
        if (res.success) {
            updatePlayerIdBadge(null);
            const inpS = document.getElementById("inputPlayerName");
            const inpO = document.getElementById("overlayPlayerName");
            if (inpS) inpS.value = "";
            if (inpO) inpO.value = "";
            const hudP = document.getElementById("hudPlayer");
            if (hudP) hudP.innerText = "(Belum Join)";
        }
        return res;
    }

    async function sendDirection(dir) {
        if (window.snakeId && window.gameRunning) {
            return await makeApiCall("PATCH", `${ARENA_SERVICE_URL}/api/v1/arena/snakes/${window.snakeId}/direction?dir=${dir}`, null, true);
        }
    }

    async function fetchArenaState() {
        const res = await makeApiCall("GET", `${ARENA_SERVICE_URL}/api/v1/arena/state`, null, true);
        if (res.success && res.data) {
            window.arenaSnakes = res.data.snakes || [];
            window.arenaApples = res.data.apples || [];

            if (window.snakeId && window.gameRunning) {
                const mySnake = window.arenaSnakes.find(s => s.id === window.snakeId);
                if (!mySnake || !mySnake.alive) {
                    if (window.SnakeArena && window.SnakeArena.App && window.SnakeArena.App.endGame) {
                        window.SnakeArena.App.endGame("Serpent dinetralisir dalam pertempuran!");
                    } else if (window.endGame) {
                        window.endGame("Serpent dinetralisir dalam pertempuran!");
                    }
                } else {
                    if (mySnake.score > (window.prevScore || 0)) {
                        const delta = mySnake.score - (window.prevScore || 0);
                        if (delta >= 30) {
                            if (window.playSfx) window.playSfx('golden');
                        } else {
                            if (window.playSfx) window.playSfx('apple');
                        }
                    }
                    window.prevScore = mySnake.score;
                    window.score = mySnake.score;
                    const hudS = document.getElementById("hudScore");
                    if (hudS) hudS.innerText = window.score;
                }
            }

            const hudCount = document.getElementById("hudPlayersCount");
            if (hudCount) {
                hudCount.innerText = `${window.arenaSnakes.length} / 16`;
            }

            if (window.SnakeArena && window.SnakeArena.App && window.SnakeArena.App.renderLeaderboard) {
                window.SnakeArena.App.renderLeaderboard(res.data.leaderboard || []);
            } else if (window.renderLeaderboard) {
                window.renderLeaderboard(res.data.leaderboard || []);
            }
        }
        return res;
    }

    // Export to SnakeArena namespace
    window.SnakeArena.API = {
        PLAYER_SERVICE_URL,
        ARENA_SERVICE_URL,
        get currentVersion() { return currentVersion; },
        set currentVersion(v) { currentVersion = v; },
        get registeredPlayerId() { return registeredPlayerId; },
        set registeredPlayerId(v) { registeredPlayerId = v; },
        logApi,
        clearLogs,
        makeApiCall,
        switchVersion,
        triggerBreakingChangeTest,
        updatePlayerIdBadge,
        getSafeUsername,
        lookupPlayerByName,
        apiCreatePlayer,
        apiFetchPlayer,
        apiUpdatePlayerProfile,
        apiPartialUpdate,
        apiDeletePlayer,
        sendDirection,
        fetchArenaState
    };

    // Backward-compatible global aliases
    window.PLAYER_SERVICE_URL = PLAYER_SERVICE_URL;
    window.ARENA_SERVICE_URL = ARENA_SERVICE_URL;
    window.currentVersion = currentVersion;
    window.registeredPlayerId = registeredPlayerId;
    window.logApi = logApi;
    window.clearLogs = clearLogs;
    window.makeApiCall = makeApiCall;
    window.switchVersion = switchVersion;
    window.triggerBreakingChangeTest = triggerBreakingChangeTest;
    window.updatePlayerIdBadge = updatePlayerIdBadge;
    window.getSafeUsername = getSafeUsername;
    window.lookupPlayerByName = lookupPlayerByName;
    window.apiCreatePlayer = apiCreatePlayer;
    window.apiFetchPlayer = apiFetchPlayer;
    window.apiUpdatePlayerProfile = apiUpdatePlayerProfile;
    window.apiPartialUpdate = apiPartialUpdate;
    window.apiDeletePlayer = apiDeletePlayer;
    window.sendDirection = sendDirection;
    window.fetchArenaState = fetchArenaState;
})();
