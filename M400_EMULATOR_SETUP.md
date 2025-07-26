# 🥽 Vuzix M400 Emulator Setup Guide

## 📋 Overview
This guide helps you configure Android Studio emulator to accurately simulate the Vuzix M400 hardware for testing.

## 🎯 Critical M400 Specifications
- **Resolution**: 640x360 pixels (ULTRA LOW)
- **Orientation**: Landscape ONLY
- **Navigation**: Trackpad (not touch)
- **Display**: OLED monocular
- **Performance**: Qualcomm XR1 (limited)

## 🔧 Setup Instructions

### Step 1: Import Hardware Profile
1. Open Android Studio
2. Go to **Tools → AVD Manager**
3. Click **Create Virtual Device**
4. Click **Import Hardware Profiles** (bottom left)
5. Select the file: `vuzix-m400-profile.xml`
6. Click **OK**

### Step 2: Create M400 Emulator
1. In Device Definition, look for **"Vuzix M400"** under Phone category
2. Select it and click **Next**
3. Choose **Android 13 (API 33)** system image (download if needed)
4. Click **Next**
5. **IMPORTANT**: Set startup orientation to **Landscape**
6. Name it: "M400_Test"
7. Click **Finish**

### Step 3: Configure Emulator Settings
Edit the emulator config to optimize for M400:

```bash
# Navigate to emulator folder (typically):
# Windows: %USERPROFILE%\.android\avd\M400_Test.avd\
# Edit config.ini file and add/modify:

hw.mainKeys=yes                    # Remove nav bar
hw.keyboard=yes                    # Enable hardware keys
hw.dPad=yes                       # Enable D-pad for trackpad simulation
hw.trackBall=yes                  # Enable trackball for trackpad
hw.lcd.width=640                  # Exact M400 width
hw.lcd.height=360                 # Exact M400 height
hw.lcd.density=180                # M400 density
startup.orientation=landscape      # Force landscape
```

## 🎮 Testing Navigation
Since M400 uses trackpad, test navigation using:
- **Arrow Keys**: Simulate trackpad movement
- **Enter**: Simulate trackpad tap
- **Space**: Simulate trackpad double-tap

## ✅ Validation Checklist

Before testing your app:
- [ ] Emulator shows 640x360 resolution
- [ ] Landscape orientation enforced
- [ ] Navigation works with keyboard only
- [ ] UI elements are large enough to select
- [ ] Text is readable at small resolution
- [ ] No touch gestures required

## 🚨 Testing Guidelines

**MANDATORY TESTS:**
1. **Navigation**: Ensure all menu items accessible via arrow keys
2. **Layout**: Verify UI doesn't break at 640x360
3. **Text Size**: Check all text is readable
4. **Button Size**: Ensure buttons are selectable
5. **Performance**: Test on low-end hardware simulation

## 🔧 Troubleshooting

**If emulator doesn't start:**
- Verify Android 13 system image is installed
- Check hardware acceleration is enabled
- Try creating new AVD if issues persist

**If resolution is wrong:**
- Edit config.ini manually with exact values
- Restart emulator completely

**If navigation doesn't work:**
- Enable hardware keys in config
- Use keyboard instead of mouse
- Test with arrow keys + enter

## 📊 Performance Considerations

**M400 vs Regular Emulator:**
- M400 has limited CPU (Qualcomm XR1)
- Battery life is critical
- Avoid heavy animations
- Optimize rendering performance

## 🎯 Ready for Development

Once setup is complete, always test new features on this M400 emulator profile to ensure compatibility with the real device.

Remember: **What works in standard emulator may NOT work on M400!**