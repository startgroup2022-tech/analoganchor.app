import os
import subprocess
import base64
import shutil

icon_path = r'C:\com.analoganchor.app\Analog_Anchor_Icon.png'
with open(icon_path, 'rb') as f:
    icon_b64 = base64.b64encode(f.read()).decode('utf-8')

html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Analog Anchor - Android v2.0 Production Baseline & System Specification</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800;900&family=JetBrains+Mono:wght@400;600;700&display=swap" rel="stylesheet">
<style>
  @page {{
    size: 1920px 1080px;
    margin: 0;
  }}
  * {{
    box-sizing: border-box;
    margin: 0;
    padding: 0;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }}
  body {{
    width: 1920px;
    height: 1080px;
    max-height: 1080px;
    overflow: hidden;
    background-color: #0B1012;
    background-image: 
      radial-gradient(circle at 50% 25%, #14242B 0%, #0B1012 70%),
      radial-gradient(circle, rgba(125, 238, 255, 0.05) 1px, transparent 1px);
    background-size: 100% 100%, 24px 24px;
    color: #F3F8FA;
    font-family: 'Outfit', -apple-system, BlinkMacSystemFont, sans-serif;
    padding: 22px 28px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }}

  /* HEADER */
  header {{
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 14px;
    border-bottom: 1px solid rgba(43, 58, 64, 0.8);
    height: 68px;
  }}
  .brand {{
    display: flex;
    align-items: center;
    gap: 16px;
  }}
  .brand img {{
    width: 48px;
    height: 48px;
    border-radius: 12px;
    border: 1px solid rgba(125, 238, 255, 0.4);
    box-shadow: 0 0 16px rgba(125, 238, 255, 0.25);
  }}
  .brand-title {{
    font-size: 24px;
    font-weight: 900;
    letter-spacing: 2px;
    color: #F3F8FA;
    text-transform: uppercase;
    display: flex;
    align-items: center;
    gap: 10px;
  }}
  .brand-title span.accent {{
    color: #7DEEFF;
  }}
  .brand-sub {{
    font-size: 12px;
    color: #A2AFB6;
    font-weight: 500;
    letter-spacing: 0.6px;
  }}
  .badges {{
    display: flex;
    gap: 10px;
    align-items: center;
  }}
  .badge {{
    font-family: 'JetBrains Mono', monospace;
    font-size: 10px;
    font-weight: 700;
    padding: 6px 12px;
    border-radius: 6px;
    background: rgba(23, 33, 38, 0.9);
    border: 1px solid rgba(43, 58, 64, 0.9);
    color: #C4D0D5;
    letter-spacing: 0.5px;
    display: flex;
    align-items: center;
    gap: 6px;
  }}
  .badge.cyan {{
    border-color: rgba(125, 238, 255, 0.4);
    color: #7DEEFF;
    background: rgba(125, 238, 255, 0.08);
  }}
  .badge.amber {{
    border-color: rgba(243, 182, 92, 0.4);
    color: #F3B65C;
    background: rgba(243, 182, 92, 0.08);
  }}
  .badge-dot {{
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
    box-shadow: 0 0 6px currentColor;
  }}

  /* GRID LAYOUT */
  .main-grid {{
    display: grid;
    grid-template-columns: 370px 480px 480px 470px;
    gap: 18px;
    height: 930px;
  }}

  .panel {{
    background: rgba(17, 25, 29, 0.88);
    border: 1px solid rgba(43, 58, 64, 0.85);
    border-radius: 16px;
    padding: 16px 18px;
    display: flex;
    flex-direction: column;
    position: relative;
    backdrop-filter: blur(12px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
  }}
  .panel::before {{
    content: '';
    position: absolute;
    top: 0; left: 20px; right: 20px;
    height: 1px;
    background: linear-gradient(90deg, transparent, rgba(125, 238, 255, 0.35), transparent);
  }}
  .panel-header {{
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid rgba(43, 58, 64, 0.5);
  }}
  .panel-num {{
    font-family: 'JetBrains Mono', monospace;
    font-size: 11px;
    color: #7DEEFF;
    font-weight: 700;
  }}
  .panel-title {{
    font-size: 13px;
    font-weight: 800;
    letter-spacing: 1px;
    text-transform: uppercase;
    color: #F3F8FA;
  }}
  .panel-tag {{
    font-family: 'JetBrains Mono', monospace;
    font-size: 9px;
    color: #A2AFB6;
    background: rgba(23, 33, 38, 0.6);
    padding: 3px 6px;
    border-radius: 4px;
  }}

  /* SECTION 01: COLOR SYSTEM */
  .color-swatches {{
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 7px;
    margin-bottom: 10px;
  }}
  .swatch {{
    background: rgba(23, 33, 38, 0.7);
    border: 1px solid rgba(43, 58, 64, 0.6);
    border-radius: 9px;
    padding: 7px 8px;
    display: flex;
    gap: 8px;
    align-items: center;
  }}
  .swatch-color {{
    width: 26px;
    height: 26px;
    border-radius: 6px;
    border: 1px solid rgba(255, 255, 255, 0.15);
    flex-shrink: 0;
  }}
  .swatch-info {{
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }}
  .swatch-name {{
    font-size: 10.5px;
    font-weight: 700;
    color: #F3F8FA;
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden;
  }}
  .swatch-hex {{
    font-family: 'JetBrains Mono', monospace;
    font-size: 9.5px;
    color: #7DEEFF;
  }}
  .swatch-role {{
    font-size: 8.5px;
    color: #A2AFB6;
  }}
  .gradient-strip {{
    height: 22px;
    border-radius: 7px;
    margin: 5px 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 10px;
    font-family: 'JetBrains Mono', monospace;
    font-size: 8.5px;
    font-weight: 700;
    border: 1px solid rgba(255, 255, 255, 0.2);
  }}
  .grad-cyan {{
    background: linear-gradient(90deg, #7DEEFF, #1FE6FF);
    color: #0B1012;
  }}
  .grad-radial {{
    background: linear-gradient(90deg, #0E1A1E, #0B1012);
    border-color: rgba(43, 58, 64, 0.8);
    color: #A2AFB6;
  }}
  .grad-steel {{
    background: linear-gradient(90deg, #F3F8FA, #A2AFB6);
    color: #0B1012;
  }}
  .theme-note {{
    margin-top: auto;
    padding: 9px 11px;
    border-radius: 8px;
    background: rgba(125, 238, 255, 0.05);
    border: 1px dashed rgba(125, 238, 255, 0.3);
    font-size: 10px;
    line-height: 1.4;
    color: #D1DCE1;
  }}
  .theme-note strong {{
    color: #7DEEFF;
  }}

  /* SECTION 02: TYPOGRAPHY & UI KIT */
  .type-item {{
    margin-bottom: 8px;
    padding-bottom: 6px;
    border-bottom: 1px solid rgba(43, 58, 64, 0.4);
  }}
  .type-meta {{
    display: flex;
    justify-content: space-between;
    font-family: 'JetBrains Mono', monospace;
    font-size: 8.5px;
    color: #7DEEFF;
    margin-bottom: 2px;
  }}
  .type-preview-display {{
    font-size: 26px;
    font-weight: 900;
    letter-spacing: -0.5px;
    color: #F3F8FA;
    line-height: 1.1;
  }}
  .type-preview-title {{
    font-size: 15px;
    font-weight: 800;
    color: #F3F8FA;
  }}
  .type-preview-body {{
    font-size: 11.5px;
    font-weight: 500;
    color: #D1DCE1;
    line-height: 1.35;
  }}
  
  .ui-kit-group {{
    margin-top: 8px;
  }}
  .ui-kit-title {{
    font-size: 10.5px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.8px;
    color: #7DEEFF;
    margin-bottom: 6px;
    display: flex;
    align-items: center;
    gap: 6px;
  }}
  .live-components {{
    display: flex;
    flex-direction: column;
    gap: 7px;
  }}
  .btn-sample {{
    background: linear-gradient(90deg, #7DEEFF, #1FE6FF);
    color: #0B1012;
    font-weight: 800;
    font-size: 11.5px;
    padding: 9px 16px;
    border-radius: 11px;
    text-align: center;
    letter-spacing: 0.5px;
    text-transform: uppercase;
    box-shadow: 0 2px 10px rgba(125, 238, 255, 0.3), inset 0 1px 1px rgba(255, 255, 255, 0.6);
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
  }}
  .card-sample {{
    background: rgba(17, 25, 29, 0.85);
    border: 1px solid rgba(43, 58, 64, 0.9);
    border-top: 1px solid rgba(125, 238, 255, 0.4);
    border-radius: 11px;
    padding: 8px 12px;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }}
  .token-sample {{
    display: flex;
    align-items: center;
    gap: 9px;
  }}
  .token-ring {{
    width: 28px;
    height: 28px;
    border-radius: 50%;
    border: 2px solid #7DEEFF;
    box-shadow: 0 0 8px rgba(125, 238, 255, 0.4);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 11px;
    color: #7DEEFF;
    font-weight: 900;
  }}
  .hud-pill {{
    display: inline-flex;
    align-items: center;
    gap: 5px;
    background: rgba(11, 16, 18, 0.9);
    border: 1px solid rgba(125, 238, 255, 0.5);
    padding: 3px 8px;
    border-radius: 20px;
    font-family: 'JetBrains Mono', monospace;
    font-size: 10px;
    color: #7DEEFF;
  }}

  /* SECTION 03: ARCHITECTURE & MECHANICS */
  .mech-item {{
    background: rgba(23, 33, 38, 0.6);
    border: 1px solid rgba(43, 58, 64, 0.6);
    border-radius: 9px;
    padding: 8px 10px;
    margin-bottom: 7px;
  }}
  .mech-header {{
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 3px;
  }}
  .mech-title {{
    font-size: 11px;
    font-weight: 700;
    color: #F3F8FA;
    display: flex;
    align-items: center;
    gap: 6px;
  }}
  .mech-code {{
    font-family: 'JetBrains Mono', monospace;
    font-size: 8.5px;
    color: #F3B65C;
    background: rgba(243, 182, 92, 0.1);
    padding: 2px 5px;
    border-radius: 4px;
  }}
  .mech-desc {{
    font-size: 9.8px;
    color: #D1DCE1;
    line-height: 1.35;
  }}
  .mech-desc strong {{
    color: #7DEEFF;
  }}

  /* SECTION 04: PRODUCTION MOBILE SCREENS */
  .phone-container {{
    display: flex;
    gap: 10px;
    justify-content: space-between;
    margin-bottom: 10px;
  }}
  .phone-mockup {{
    flex: 1;
    background: #081013;
    border: 2px solid rgba(43, 58, 64, 0.9);
    border-radius: 16px;
    padding: 9px 7px;
    display: flex;
    flex-direction: column;
    align-items: center;
    position: relative;
    box-shadow: inset 0 0 16px rgba(0,0,0,0.8), 0 4px 14px rgba(0,0,0,0.5);
  }}
  .phone-notch {{
    width: 36px;
    height: 4px;
    background: rgba(43, 58, 64, 0.8);
    border-radius: 3px;
    margin-bottom: 6px;
  }}
  .phone-screen-title {{
    font-size: 9px;
    font-weight: 800;
    color: #7DEEFF;
    text-transform: uppercase;
    letter-spacing: 0.8px;
    margin-bottom: 5px;
  }}
  .timer-circle {{
    width: 62px;
    height: 62px;
    border-radius: 50%;
    border: 3px solid rgba(125, 238, 255, 0.2);
    border-top-color: #7DEEFF;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    margin: 4px 0;
  }}
  .timer-time {{
    font-family: 'JetBrains Mono', monospace;
    font-size: 12px;
    font-weight: 800;
    color: #F3F8FA;
  }}
  .timer-label {{
    font-size: 7px;
    color: #7DEEFF;
    text-transform: uppercase;
  }}
  .phone-card {{
    width: 100%;
    background: rgba(17, 25, 29, 0.9);
    border: 1px solid rgba(43, 58, 64, 0.8);
    border-radius: 7px;
    padding: 5px 6px;
    margin-top: 4px;
    font-size: 8px;
    color: #D1DCE1;
  }}

  /* SECTION 05: IOS TRANSLATION & CREATIVE LATITUDE */
  .ios-brief {{
    background: rgba(125, 238, 255, 0.04);
    border: 1px solid rgba(125, 238, 255, 0.25);
    border-radius: 11px;
    padding: 10px 12px;
    margin-bottom: 10px;
  }}
  .ios-badge {{
    display: inline-block;
    font-family: 'JetBrains Mono', monospace;
    font-size: 8.5px;
    font-weight: 700;
    color: #7DEEFF;
    background: rgba(125, 238, 255, 0.12);
    padding: 2px 7px;
    border-radius: 4px;
    margin-bottom: 5px;
  }}
  .ios-brief-p {{
    font-size: 10.5px;
    line-height: 1.4;
    color: #D1DCE1;
  }}
  .ios-brief-p strong {{
    color: #F3F8FA;
  }}
  .ios-points {{
    display: flex;
    flex-direction: column;
    gap: 7px;
  }}
  .ios-point {{
    background: rgba(23, 33, 38, 0.7);
    border: 1px solid rgba(43, 58, 64, 0.7);
    border-radius: 8px;
    padding: 7px 9px;
  }}
  .ios-point-title {{
    font-size: 10.5px;
    font-weight: 700;
    color: #7DEEFF;
    margin-bottom: 2px;
  }}
  .ios-point-desc {{
    font-size: 9.5px;
    color: #A2AFB6;
    line-height: 1.35;
  }}

  /* FOOTER */
  footer {{
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 22px;
    border-top: 1px solid rgba(43, 58, 64, 0.6);
    font-family: 'JetBrains Mono', monospace;
    font-size: 9px;
    color: #8BA0A8;
    padding-top: 4px;
  }}
  footer span.val {{
    color: #7DEEFF;
  }}
</style>
</head>
<body>

  <!-- HEADER -->
  <header>
    <div class="brand">
      <img src="data:image/png;base64,{icon_b64}" alt="Analog Anchor Logo" />
      <div>
        <div class="brand-title">Analog Anchor <span class="accent">//</span> Production Baseline</div>
        <div class="brand-sub">Current Android v2.0 Architecture & Design System &mdash; Reference Sheet for iOS Product Team</div>
      </div>
    </div>
    <div class="badges">
      <div class="badge cyan"><div class="badge-dot"></div> ANDROID JETPACK COMPOSE / M3</div>
      <div class="badge"><div class="badge-dot"></div> TARGET: iOS / SWIFTUI & HIG</div>
      <div class="badge amber"><div class="badge-dot"></div> FEMALE-LED CLIENT DEMOGRAPHIC</div>
      <div class="badge"><div class="badge-dot"></div> ONE-PAGE SUPER AI SPEC</div>
    </div>
  </header>

  <!-- MAIN 4-COLUMN HIGH-DENSITY GRID -->
  <div class="main-grid">

    <!-- COL 1: COLOR FOUNDATION -->
    <div class="panel">
      <div class="panel-header">
        <span class="panel-num">01</span>
        <span class="panel-title">Color System Tokens</span>
        <span class="panel-tag">Color.kt</span>
      </div>

      <div class="color-swatches">
        <div class="swatch">
          <div class="swatch-color" style="background: #0B1012;"></div>
          <div class="swatch-info">
            <span class="swatch-name">ObsidianBg</span>
            <span class="swatch-hex">#0B1012</span>
            <span class="swatch-role">Root Canvas</span>
          </div>
        </div>
        <div class="swatch">
          <div class="swatch-color" style="background: #11191D;"></div>
          <div class="swatch-info">
            <span class="swatch-name">ObsidianSurface</span>
            <span class="swatch-hex">#11191D</span>
            <span class="swatch-role">Glass Base 85%</span>
          </div>
        </div>
        <div class="swatch">
          <div class="swatch-color" style="background: #172126;"></div>
          <div class="swatch-info">
            <span class="swatch-name">DeepPanel</span>
            <span class="swatch-hex">#172126</span>
            <span class="swatch-role">Modals & Sheets</span>
          </div>
        </div>
        <div class="swatch">
          <div class="swatch-color" style="background: #2B3A40;"></div>
          <div class="swatch-info">
            <span class="swatch-name">ObsidianBorder</span>
            <span class="swatch-hex">#2B3A40</span>
            <span class="swatch-role">Specular Borders</span>
          </div>
        </div>
        <div class="swatch">
          <div class="swatch-color" style="background: #7DEEFF; box-shadow: 0 0 10px #7DEEFF;"></div>
          <div class="swatch-info">
            <span class="swatch-name">GlowGreen</span>
            <span class="swatch-hex">#7DEEFF</span>
            <span class="swatch-role">Cyan Accent Aura</span>
          </div>
        </div>
        <div class="swatch">
          <div class="swatch-color" style="background: #1FE6FF; box-shadow: 0 0 10px #1FE6FF;"></div>
          <div class="swatch-info">
            <span class="swatch-name">CyanAura</span>
            <span class="swatch-hex">#1FE6FF</span>
            <span class="swatch-role">Gradient Stop</span>
          </div>
        </div>
        <div class="swatch">
          <div class="swatch-color" style="background: #F3F8FA;"></div>
          <div class="swatch-info">
            <span class="swatch-name">WarmOffWhite</span>
            <span class="swatch-hex">#F3F8FA</span>
            <span class="swatch-role">Primary Text</span>
          </div>
        </div>
        <div class="swatch">
          <div class="swatch-color" style="background: #D1DCE1;"></div>
          <div class="swatch-info">
            <span class="swatch-name">WarmSilverText</span>
            <span class="swatch-hex">#D1DCE1</span>
            <span class="swatch-role">Secondary Text</span>
          </div>
        </div>
        <div class="swatch">
          <div class="swatch-color" style="background: #F3B65C;"></div>
          <div class="swatch-info">
            <span class="swatch-name">SignalAmber</span>
            <span class="swatch-hex">#F3B65C</span>
            <span class="swatch-role">Caution & Timers</span>
          </div>
        </div>
        <div class="swatch">
          <div class="swatch-color" style="background: #FF5252;"></div>
          <div class="swatch-info">
            <span class="swatch-name">SignalRed</span>
            <span class="swatch-hex">#FF5252</span>
            <span class="swatch-role">Hard Lockouts</span>
          </div>
        </div>
      </div>

      <div style="font-size: 9.5px; font-weight: 700; color: #A2AFB6; text-transform: uppercase; margin: 4px 0 3px;">Gradients & Specular Brushes</div>
      <div class="gradient-strip grad-cyan">
        <span>GlowGreenGradientBrush</span>
        <span>#7DEEFF &rarr; #1FE6FF</span>
      </div>
      <div class="gradient-strip grad-radial">
        <span>PremiumDarkRadialBrush</span>
        <span>#0E1A1E &rarr; #0B1012 (1800f)</span>
      </div>
      <div class="gradient-strip grad-steel">
        <span>SteelGradientBrush</span>
        <span>#F3F8FA &rarr; #A2AFB6</span>
      </div>

      <div class="theme-note">
        <strong>Palette Architecture:</strong> Built in Android Compose around tactile minimalism&mdash;deep matte obsidian backdrop combined with crisp cyan-mint light refraction. Light mode counterpart alternates to clean alabaster (<code>#F3F8FA</code>) and frosted titanium.
      </div>
    </div>

    <!-- COL 2: TYPOGRAPHY & UI KIT -->
    <div class="panel">
      <div class="panel-header">
        <span class="panel-num">02</span>
        <span class="panel-title">Typography & UI Kit</span>
        <span class="panel-tag">Type.kt / GlassCard.kt</span>
      </div>

      <div class="type-item">
        <div class="type-meta">
          <span>DISPLAY LARGE &bull; 40sp / 46sp</span>
          <span>Outfit Black 900</span>
        </div>
        <div class="type-preview-display">00:45:00</div>
      </div>

      <div class="type-item">
        <div class="type-meta">
          <span>TITLE LARGE &bull; 24sp / 28sp</span>
          <span>Outfit Bold 700</span>
        </div>
        <div class="type-preview-title">Two-Tap Focus Anchor</div>
      </div>

      <div class="type-item" style="border-bottom: none; margin-bottom: 4px;">
        <div class="type-meta">
          <span>BODY LARGE &bull; 15sp / 22sp</span>
          <span>Outfit Medium 500</span>
        </div>
        <div class="type-preview-body">Gross-motor physical contact breaks autopilot dopamine loops. Willpower is finite; physical anchors preserve clarity.</div>
      </div>

      <div class="ui-kit-group">
        <div class="ui-kit-title">&bull; Compose Tactile Components</div>
        <div class="live-components">
          
          <div class="btn-sample">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><polygon points="10 8 16 12 10 16 10 8" fill="currentColor"/></svg>
            START DEEP WORK BLOCK
          </div>

          <div class="card-sample">
            <div class="token-sample">
              <div class="token-ring">&oplus;</div>
              <div>
                <div style="font-size: 10.5px; font-weight: 700; color: #F3F8FA;">Desk NFC Anchor</div>
                <div style="font-size: 8.5px; color: #7DEEFF;">Tap 1 Ignition Verified</div>
              </div>
            </div>
            <div class="hud-pill"><span style="color:#7DEEFF;">&bull;</span> ACTIVE 25m</div>
          </div>

          <div class="card-sample" style="border-top-color: rgba(243, 182, 92, 0.6);">
            <div>
              <div style="font-size: 10.5px; font-weight: 700; color: #F3B65C;">Mindful Gateway (5s Pause)</div>
              <div style="font-size: 8.5px; color: #A2AFB6;">Liquid breathing intercept &bull; 15s silence kick</div>
            </div>
            <span style="font-family: 'JetBrains Mono', monospace; font-size: 9.5px; color: #F3B65C;">REMAIN: 42m</span>
          </div>

        </div>
      </div>

      <div class="theme-note" style="margin-top: 8px;">
        <strong>Tactile Feedback Contract:</strong> Buttons utilize 3% scale-down micro-compression animation with zero stock ripple, replicating a physical analog switch.
      </div>
    </div>

    <!-- COL 3: PRODUCTION MOBILE SCREENS & MECHANICS -->
    <div class="panel">
      <div class="panel-header">
        <span class="panel-num">03</span>
        <span class="panel-title">Production Screen Archetypes</span>
        <span class="panel-tag">Android Screens</span>
      </div>

      <div class="phone-container">
        <!-- SCREEN 1 -->
        <div class="phone-mockup">
          <div class="phone-notch"></div>
          <div class="phone-screen-title">1. Anchor Tab</div>
          <div class="timer-circle">
            <div class="timer-time">45:00</div>
            <div class="timer-label">FOCUS</div>
          </div>
          <div class="phone-card">
            <strong style="color:#7DEEFF;">Analog Checklist</strong><br>
            &bull; Whiteboard Open<br>
            &bull; Phone Face-Down
          </div>
          <div class="phone-card" style="border-color: rgba(125,238,255,0.4);">
            <span style="color:#7DEEFF;">&#10003; Tap 1: Desk NFC</span>
          </div>
        </div>

        <!-- SCREEN 2 -->
        <div class="phone-mockup">
          <div class="phone-notch"></div>
          <div class="phone-screen-title">2. Mosque Tab</div>
          <div style="margin: 8px 0; text-align: center;">
            <div style="font-size: 12px; font-weight: 800; color: #7DEEFF;">الهبوط الناعم</div>
            <div style="font-size: 7.5px; color: #A2AFB6;">Soft Landing &bull; Dhuhr</div>
          </div>
          <div class="phone-card">
            <strong>2X Harmonic Ascent</strong><br>
            E5-G#5-B5-G#5-B5-E6<br>
            Direct DND Shade Action
          </div>
          <div class="phone-card" style="border-color: rgba(243,182,92,0.4);">
            <span style="color:#F3B65C;">Fajr NFC Lock Active</span>
          </div>
        </div>

        <!-- SCREEN 3 -->
        <div class="phone-mockup">
          <div class="phone-notch"></div>
          <div class="phone-screen-title">3. Day Guard</div>
          <div style="margin: 8px 0; text-align: center;">
            <div style="font-size: 12px; font-weight: 800; color: #F3B65C;">Mindful Pause</div>
            <div style="font-size: 7.5px; color: #A2AFB6;">Dynamic Duration Matrix</div>
          </div>
          <div class="phone-card">
            <span style="color:#7DEEFF;">Breathing Wave: 5.0s</span><br>
            Remaining Ceiling: 38m
          </div>
          <div class="phone-card">
            Shuffled: [5m] [10m]<br>
            15s Silence Auto-Exit
          </div>
        </div>
      </div>

      <div style="font-size: 9.5px; font-weight: 700; color: #A2AFB6; text-transform: uppercase; margin: 3px 0 5px;">Underlying Behavioral Engine</div>
      <div class="mech-item">
        <div class="mech-header">
          <span class="mech-title">Physical Presence Protocol</span>
          <span class="mech-code">Desk NFC Tap 1 & 2</span>
        </div>
        <div class="mech-desc">Requires physical tap at desk to ignite session; completion alarm sounds until physical 2nd tap seals whiteboard reflection.</div>
      </div>

      <div class="mech-item">
        <div class="mech-header">
          <span class="mech-title">Circadian & Sacred Anchors</span>
          <span class="mech-code">Offline Astro GPS</span>
        </div>
        <div class="mech-desc">Fajr wake challenge (Bedroom NFC + 25 steps + Ayah typing) + Pre-Adhan Soft Landing (5m DND transition).</div>
      </div>

      <div class="mech-item" style="margin-bottom: 0;">
        <div class="mech-header">
          <span class="mech-title">Accountability Ledger</span>
          <span class="mech-code">500 PTS / Friday Reset</span>
        </div>
        <div class="mech-desc">Tracks positive habit adherence. Resets cleanly every Friday morning before Jumu'ah to eliminate despair.</div>
      </div>

    </div>

    <!-- COL 4: IOS CREATIVE BRIEF & TRANSLATION -->
    <div class="panel">
      <div class="panel-header">
        <span class="panel-num">04</span>
        <span class="panel-title">iOS Translation & Brief</span>
        <span class="panel-tag">Creative Freedom</span>
      </div>

      <div class="ios-brief">
        <div class="ios-badge">STRATEGIC CONTEXT & CLIENT PROFILE</div>
        <div class="ios-brief-p">
          Our primary commercial audience is <strong>predominantly iOS-based and heavily female/ladies</strong>. They seek mindful calm, elegant life architecture, and intentional self-sovereignty&mdash;not aggressive gamer hardware aesthetics.
        </div>
      </div>

      <div style="font-size: 10.5px; font-weight: 800; color: #F3F8FA; text-transform: uppercase; margin-bottom: 7px;">
        Creative Latitude for iOS Developer / Designer:
      </div>

      <div class="ios-points">
        <div class="ios-point">
          <div class="ios-point-title">1. Visual Softness & Tonal Elevation</div>
          <div class="ios-point-desc">
            Do not feel constrained to duplicate Android's high-contrast dark obsidian. Feel completely free to explore warmer taupe, subtle champagne/rose tints, or an ultra-clean light mode with Apple's native <code>.ultraThinMaterial</code> frosted glass.
          </div>
        </div>

        <div class="ios-point">
          <div class="ios-point-title">2. Native Apple Screen Time API</div>
          <div class="ios-point-desc">
            Replace Android's Device Admin & Accessibility Service with native Apple frameworks: <code>FamilyControls</code>, <code>ManagedSettings</code>, and <code>DeviceActivityMonitor</code> for battery-efficient system-level app shielding.
          </div>
        </div>

        <div class="ios-point">
          <div class="ios-point-title">3. Apple Ergonomics & Dynamic Island</div>
          <div class="ios-point-desc">
            Leverage Live Activities & Dynamic Island for the floating focus timer pill. Integrate NFC Shortcuts, Action Button triggers, and Apple Watch companion taps for seamless tactile rituals.
          </div>
        </div>

        <div class="ios-point">
          <div class="ios-point-title">4. Core Behavioral Invariants</div>
          <div class="ios-point-desc">
            The foundational mechanics must remain uncompromised: <strong>Physical Grounding</strong> (NFC / motion), <strong>Non-Reversible Pre-Commitment</strong> (locked anchors), and <strong>Sacred Circadian Pauses</strong>.
          </div>
        </div>
      </div>

      <div class="theme-note" style="margin-top: auto; border-color: rgba(243, 182, 92, 0.4); background: rgba(243, 182, 92, 0.05);">
        <strong style="color: #F3B65C;">Developer Scope Note:</strong> This document serves as your functional and behavioral baseline. Build upon this foundation to craft a world-class, serene iOS experience.
      </div>
    </div>

  </div>

  <!-- FOOTER -->
  <footer>
    <div>ANALOG ANCHOR &bull; SYSTEM BASELINE SPECIFICATION &bull; PRODUCTION READY</div>
    <div>ACTIVE REPO: <span class="val">github.com/arazi-hash/com.analoganchor.app</span> &bull; COMMIT: <span class="val">v2.0-RELEASE</span></div>
    <div>PREPARED FOR: <span class="val">SUPER AI / iOS PRODUCT TEAM</span></div>
  </footer>

</body>
</html>"""

out_html = r'C:\com.analoganchor.app\Analog_Anchor_Android_Baseline_Spec.html'
out_pdf = r'C:\com.analoganchor.app\Analog_Anchor_Android_Baseline_Spec.pdf'
out_png = r'C:\com.analoganchor.app\Analog_Anchor_Android_Baseline_Spec.png'

with open(out_html, 'w', encoding='utf-8') as f:
    f.write(html)

edge = r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'

# Generate PDF (exact 1920x1080 page size)
subprocess.run([edge, '--headless', '--disable-gpu', f'--print-to-pdf={out_pdf}', '--no-pdf-header-footer', out_html], check=True)

# Generate PNG Preview (1920x1080)
subprocess.run([edge, '--headless', '--disable-gpu', f'--screenshot={out_png}', '--window-size=1920,1080', out_html], check=True)

# Copy to worktree as well
worktree_dir = r'C:\Users\DELL\.gemini\antigravity\worktrees\com.analoganchor.app\refine_android_pdf'
shutil.copy(out_html, os.path.join(worktree_dir, 'Analog_Anchor_Android_Baseline_Spec.html'))
shutil.copy(out_pdf, os.path.join(worktree_dir, 'Analog_Anchor_Android_Baseline_Spec.pdf'))
shutil.copy(out_png, os.path.join(worktree_dir, 'Analog_Anchor_Android_Baseline_Spec.png'))

print('Generated successfully in both C:\\com.analoganchor.app and worktree!')
print('PDF Size:', os.path.getsize(out_pdf))
print('PNG Size:', os.path.getsize(out_png))
