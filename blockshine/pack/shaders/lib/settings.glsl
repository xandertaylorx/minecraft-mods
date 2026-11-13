// Blockshine settings. Every #define with a [list] or a toggle shows up in the in-game shader options.

// ---- Shadows ----
#define SHADOWS
#define SHADOW_FILTER 1 // [0 1 2]
#define SHADOW_STRENGTH 1.0 // [0.5 0.6 0.7 0.8 0.9 1.0]

// ---- Lighting ----
#define SUN_BRIGHTNESS 1.0 // [0.6 0.7 0.8 0.9 1.0 1.1 1.2 1.3 1.4 1.5]
#define AMBIENT_BRIGHTNESS 1.0 // [0.6 0.8 1.0 1.2 1.4 1.6]
#define TORCH_BRIGHTNESS 1.0 // [0.6 0.8 1.0 1.2 1.4 1.6]
#define EMISSIVE_BLOCKS
#define MIN_LIGHT 0.015 // [0.0 0.005 0.01 0.015 0.025 0.04]

// ---- World ----
#define WAVING_PLANTS
#define WAVING_LEAVES
#define WAVE_SPEED 1.0 // [0.5 0.75 1.0 1.25 1.5 2.0]
#define WATER_WAVES
#define WATER_REFLECTIONS
#define WATER_SSR
#define SSR_STEPS 12 // [8 12 16 20 24]
#define WATER_DEPTH_FOG 1.0 // [0.5 0.75 1.0 1.5 2.0]
#define SOFT_CLOUDS
#define CLOUD_DETAIL 4 // [3 4 5]
#define CLOUD_AMOUNT 1.0 // [0.0 0.5 1.0 1.5 2.0]
#define FOLIAGE_GLOW
#define FOG_DENSITY 1.0 // [0.0 0.5 0.75 1.0 1.5 2.0]

// ---- Post ----
#define BLOOM
#define BLOOM_STRENGTH 0.14 // [0.04 0.06 0.08 0.10 0.12 0.15 0.20]
#define LIGHT_SHAFTS
#define LIGHT_SHAFT_SAMPLES 6 // [6 8 12 16]
//#define VOLUMETRIC_LIGHT
#define VL_SAMPLES 8 // [6 8 10 12 16]
#define VL_STRENGTH 1.0 // [0.5 0.75 1.0 1.25 1.5 2.0]
#define LENS_FLARE
#define LENS_FLARE_STRENGTH 1.0 // [0.5 0.75 1.0 1.5 2.0]
#define LIGHT_SHAFT_STRENGTH 1.0 // [0.5 0.75 1.0 1.25 1.5]
#define EXPOSURE 1.0 // [0.7 0.8 0.9 1.0 1.1 1.2 1.3 1.5]
#define SATURATION 1.05 // [0.8 0.9 1.0 1.05 1.10 1.15 1.20 1.30]
#define VIGNETTE

// Iris only lists a toggle as an option if it's checked with #ifdef somewhere.
// SHADOWS, BLOOM and LIGHT_SHAFTS switch whole passes on/off in shaders.properties, so reference them here.
#ifdef SHADOWS
#endif
#ifdef BLOOM
#endif
#ifdef LIGHT_SHAFTS
#endif
#ifdef VOLUMETRIC_LIGHT
#endif
#ifdef LENS_FLARE
#endif
#ifdef SOFT_CLOUDS
#endif
#ifdef WATER_SSR
#endif
#ifdef FOLIAGE_GLOW
#endif
