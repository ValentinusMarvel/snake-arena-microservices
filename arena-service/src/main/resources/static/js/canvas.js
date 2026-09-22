/**
 * Snake Arena: Clash of Serpents
 * Module: 2D Tactical Canvas Rendering Engine
 * --------------------------------------------------------------------------
 * Renders high-frequency 60 FPS arena graphics:
 * - Technical coordinate grid & perimeter hazard stripes
 * - Collectibles: Ruby Gem Apples & Glowing Golden Core Diamonds with satellites
 * - Serpent bodies with tapered segmentation, directional armored heads, and luminous optic eyes
 * - Tactical skill forcefield / aura effects and floating HUD callsigns
 */

window.SnakeArena = window.SnakeArena || {};

(function() {
    const canvas = document.getElementById("gameCanvas");
    const ctx = canvas ? canvas.getContext("2d") : null;
    const gridSize = 30;
    const tileSize = canvas ? canvas.width / gridSize : 20;

    let arenaSnakes = [];
    let arenaApples = [];
    let prevScore = 0;

    function renderLoop() {
        render();
        requestAnimationFrame(renderLoop);
    }

    function render() {
        if (!ctx || !canvas) return;

        const enclosureEl = document.getElementById("canvasEnclosure");
        if (enclosureEl) {
            if (window.gameRunning && window.skillActive) {
                enclosureEl.style.boxShadow = "0 12px 30px rgba(0, 0, 0, 0.5)";
                enclosureEl.style.borderColor = window.selectedColor || "#00e575";
            } else {
                enclosureEl.style.boxShadow = "0 12px 30px rgba(0, 0, 0, 0.4)";
                enclosureEl.style.borderColor = "var(--border-muted)";
            }
        }

        // Clean Matte Slate Canvas
        ctx.fillStyle = "#0a0d14";
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        // Subtle Technical Coordinate Grid
        ctx.strokeStyle = "rgba(255, 255, 255, 0.028)";
        ctx.lineWidth = 1;
        for (let i = 0; i <= gridSize; i++) {
            const p = i * tileSize;
            ctx.beginPath();
            ctx.moveTo(p, 0);
            ctx.lineTo(p, canvas.height);
            ctx.stroke();

            ctx.beginPath();
            ctx.moveTo(0, p);
            ctx.lineTo(canvas.width, p);
            ctx.stroke();
        }

        // Perimeter Hazard Warning Lines
        ctx.strokeStyle = "rgba(255, 51, 85, 0.22)";
        ctx.lineWidth = 2;
        ctx.strokeRect(1, 1, canvas.width - 2, canvas.height - 2);

        const currentSnakes = window.arenaSnakes || arenaSnakes;
        const currentApples = window.arenaApples || arenaApples;

        // 1. RENDER APPLES / COLLECTIBLES
        currentApples.forEach(apple => {
            const cx = apple.x * tileSize + tileSize / 2;
            const cy = apple.y * tileSize + tileSize / 2;
            const now = Date.now();

            if (apple.type === "GOLDEN") {
                // GOLDEN APPLE: Radiant Core + Rotating Micro-Diamonds
                const pulse = 1 + Math.sin(now / 180) * 0.08;
                const r = (tileSize / 2.3) * pulse;

                // Radiant Aura
                const aura = ctx.createRadialGradient(cx, cy, 0, cx, cy, r * 2.2);
                aura.addColorStop(0, "rgba(251, 191, 36, 0.45)");
                aura.addColorStop(0.5, "rgba(245, 158, 11, 0.18)");
                aura.addColorStop(1, "transparent");
                ctx.fillStyle = aura;
                ctx.beginPath();
                ctx.arc(cx, cy, r * 2.2, 0, Math.PI * 2);
                ctx.fill();

                // Gold Jewel Body
                const grad = ctx.createRadialGradient(cx - r * 0.3, cy - r * 0.3, r * 0.1, cx, cy, r);
                grad.addColorStop(0, "#fef08a");
                grad.addColorStop(0.4, "#fbbf24");
                grad.addColorStop(0.85, "#d97706");
                grad.addColorStop(1, "#78350f");

                ctx.fillStyle = grad;
                ctx.beginPath();
                ctx.arc(cx, cy, r, 0, Math.PI * 2);
                ctx.fill();

                // Center Geometric Gold Core Diamond
                ctx.save();
                ctx.translate(cx, cy);
                ctx.fillStyle = "#ffffff";
                ctx.beginPath();
                const dSize = r * 0.45;
                ctx.moveTo(0, -dSize);
                ctx.lineTo(dSize * 0.7, 0);
                ctx.lineTo(0, dSize);
                ctx.lineTo(-dSize * 0.7, 0);
                ctx.closePath();
                ctx.fill();

                // 4 Rotating Diamond Micro-Satellites
                const angle = now / 400;
                for (let i = 0; i < 4; i++) {
                    const a = angle + (i * Math.PI / 2);
                    const sx = Math.cos(a) * (r * 1.35);
                    const sy = Math.sin(a) * (r * 1.35);
                    ctx.fillStyle = "#fde047";
                    ctx.beginPath();
                    ctx.arc(sx, sy, 1.8, 0, Math.PI * 2);
                    ctx.fill();
                }
                ctx.restore();

            } else {
                // NORMAL APPLE: Polished Ruby Gem with Emerald Stem Leaf
                const r = tileSize / 2.6;

                // Subtle Ground Glow
                ctx.fillStyle = "rgba(239, 68, 68, 0.15)";
                ctx.beginPath();
                ctx.arc(cx, cy, r * 1.6, 0, Math.PI * 2);
                ctx.fill();

                // Ruby Gem Gradient
                const grad = ctx.createRadialGradient(cx - r * 0.3, cy - r * 0.3, r * 0.1, cx, cy, r);
                grad.addColorStop(0, "#fca5a5");
                grad.addColorStop(0.35, "#ef4444");
                grad.addColorStop(0.8, "#b91c1c");
                grad.addColorStop(1, "#450a0a");

                ctx.fillStyle = grad;
                ctx.beginPath();
                ctx.arc(cx, cy + 1, r, 0, Math.PI * 2);
                ctx.fill();

                // Emerald Stem Leaf
                ctx.fillStyle = "#10b981";
                ctx.beginPath();
                ctx.ellipse(cx + 2, cy - r + 1, 3, 1.5, Math.PI / 4, 0, Math.PI * 2);
                ctx.fill();
            }
        });

        // 2. RENDER SNAKES: Directional Head, Eyes, & Segmented Exoskeleton
        currentSnakes.forEach(s => {
            if (!s.alive || !s.body || s.body.length === 0) return;

            const isMe = (s.id === window.snakeId);
            const isSkillOn = isMe ? window.skillActive : s.skillActive;
            const primaryColor = s.color || "#00e575";

            const head = s.body[0];
            const neck = s.body[1] || head;

            // Determine Direction Vector
            let dirX = head.x - neck.x;
            let dirY = head.y - neck.y;
            if (dirX === 0 && dirY === 0) {
                if (s.direction === "UP") { dirY = -1; }
                else if (s.direction === "DOWN") { dirY = 1; }
                else if (s.direction === "LEFT") { dirX = -1; }
                else if (s.direction === "RIGHT") { dirX = 1; }
            }

            // A. SKILL FORCEFIELD / AURA
            if (isSkillOn) {
                ctx.save();
                const now = Date.now();
                const hx = head.x * tileSize + tileSize / 2;
                const hy = head.y * tileSize + tileSize / 2;

                // Trail Forcefield
                s.body.forEach((seg, idx) => {
                    const sx = seg.x * tileSize + tileSize / 2;
                    const sy = seg.y * tileSize + tileSize / 2;
                    ctx.fillStyle = `${primaryColor}18`;
                    ctx.beginPath();
                    ctx.arc(sx, sy, tileSize * 0.75, 0, Math.PI * 2);
                    ctx.fill();
                });

                // Bubble Shield
                const auraRadius = tileSize * 1.5 + Math.sin(now / 100) * 2;
                ctx.strokeStyle = primaryColor;
                ctx.lineWidth = 2;
                ctx.beginPath();
                ctx.arc(hx, hy, auraRadius, 0, Math.PI * 2);
                ctx.stroke();

                // Rotating Tactical Indicator
                ctx.strokeStyle = `${primaryColor}66`;
                ctx.lineWidth = 1;
                ctx.setLineDash([4, 4]);
                ctx.beginPath();
                ctx.arc(hx, hy, auraRadius + 4, now / 200, now / 200 + Math.PI * 2);
                ctx.stroke();
                ctx.setLineDash([]);
                ctx.restore();
            }

            // B. DRAW BODY SEGMENTS (Tapered from neck to tail)
            const total = s.body.length;
            for (let i = total - 1; i >= 1; i--) {
                const seg = s.body[i];
                const sx = seg.x * tileSize + tileSize / 2;
                const sy = seg.y * tileSize + tileSize / 2;

                const t = 1 - (i / total);
                const segRadius = (tileSize / 2.3) * (0.65 + 0.35 * t);

                ctx.fillStyle = primaryColor;
                ctx.beginPath();
                ctx.arc(sx, sy, segRadius, 0, Math.PI * 2);
                ctx.fill();

                // Inner Dorsal Core Highlight
                ctx.fillStyle = "rgba(255, 255, 255, 0.28)";
                ctx.beginPath();
                ctx.arc(sx, sy, segRadius * 0.45, 0, Math.PI * 2);
                ctx.fill();
            }

            // C. DRAW DIRECTION-AWARE HEAD & LUMINOUS OPTIC EYES
            const headCx = head.x * tileSize + tileSize / 2;
            const headCy = head.y * tileSize + tileSize / 2;
            const angle = Math.atan2(dirY, dirX) + Math.PI / 2;

            ctx.save();
            ctx.translate(headCx, headCy);
            ctx.rotate(angle);

            // Armored Head Shell (Pointing UP in local space)
            const hr = tileSize / 2.1;
            ctx.fillStyle = primaryColor;
            ctx.beginPath();
            ctx.ellipse(0, 0, hr * 0.95, hr * 1.15, 0, 0, Math.PI * 2);
            ctx.fill();

            ctx.fillStyle = "rgba(255, 255, 255, 0.35)";
            ctx.beginPath();
            ctx.ellipse(0, -2, hr * 0.5, hr * 0.7, 0, 0, Math.PI * 2);
            ctx.fill();

            // Luminous Optic Eyes
            const eyeSpacing = hr * 0.55;
            const eyeFwd = -hr * 0.45;

            // Eye Glow
            ctx.fillStyle = "#ffffff";
            ctx.beginPath();
            ctx.arc(-eyeSpacing, eyeFwd, 2.5, 0, Math.PI * 2);
            ctx.arc(eyeSpacing, eyeFwd, 2.5, 0, Math.PI * 2);
            ctx.fill();

            // Core Pupil
            ctx.fillStyle = "#000000";
            ctx.beginPath();
            ctx.arc(-eyeSpacing, eyeFwd - 0.5, 1.2, 0, Math.PI * 2);
            ctx.arc(eyeSpacing, eyeFwd - 0.5, 1.2, 0, Math.PI * 2);
            ctx.fill();

            ctx.restore();

            // D. FLOATING CALLSIGN & CLASS HUD BADGE
            ctx.save();
            ctx.font = isMe ? "bold 11px 'JetBrains Mono', monospace" : "9px 'JetBrains Mono', monospace";
            ctx.textAlign = "center";
            ctx.fillStyle = isMe ? "#ffffff" : "rgba(255, 255, 255, 0.65)";
            const label = isMe ? `${s.playerName} (YOU)` : s.playerName;
            ctx.fillText(label, headCx, head.y * tileSize - (isSkillOn ? 25 : 6));
            ctx.restore();
        });
    }

    // Export to SnakeArena namespace
    window.SnakeArena.Canvas = {
        canvas,
        ctx,
        gridSize,
        tileSize,
        render,
        renderLoop,
        get arenaSnakes() { return arenaSnakes; },
        set arenaSnakes(v) { arenaSnakes = v; },
        get arenaApples() { return arenaApples; },
        set arenaApples(v) { arenaApples = v; }
    };

    // Backward-compatible global aliases
    window.canvas = canvas;
    window.ctx = ctx;
    window.gridSize = gridSize;
    window.tileSize = tileSize;
    window.arenaSnakes = arenaSnakes;
    window.arenaApples = arenaApples;
    window.prevScore = prevScore;
    window.render = render;
    window.renderLoop = renderLoop;
})();
