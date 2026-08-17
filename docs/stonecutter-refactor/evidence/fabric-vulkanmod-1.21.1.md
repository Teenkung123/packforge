# Fabric VulkanMod 1.21.1 focused failure evidence

Checked: 2026-08-16

This immutable summary records a current final-JAR `fabric-vulkanmod`
compatibility attempt. It is a loader/environment failure disposition, not a
PackForge mixin failure.

## Inputs

- PackForge artifact: `packforge-fabric-1.4-beta.2-mc1.20.5-1.21.1.jar`
- PackForge artifact SHA-256: `018A5B5962B79F06C679D18ABF728F2C33A88DA0096547484FA8528E918AEE42`
- VulkanMod `0.5.5` SHA-256: `7E1EB13878EE67BEC35A6672B5FF5E28C93EE7C8D330F8E654EC4C61BCAE7618`
- Fixture SHA-256: `AAC284CF3F1E9CBD99DF72FB46388117AE02B2AD89F1BF17FC8B3E02DEE52D28`
- Fixture manifest SHA-256: `D77EF3F039E798B3E2004DC3B3A4790C829A4B35EDBE9A0A9A0F20DFE0733D66`
- Release manifest SHA-256: `5D2DCF81DC759686DBF066719D31A148B689DD9E7708B7CCC6ED67FE103D31D0`

## Result

The client exited before PackForge readiness while VulkanMod initialized its
renderer. The crash signature was:

```text
java.lang.OutOfMemoryError: Out of stack space
    at org.lwjgl.vulkan.VkInstance.getAvailableDeviceExtensions(VkInstance.java:82)
    at net.vulkanmod.vulkan.Vulkan.initVulkan(Vulkan.java:139)
```

The log contained no PackForge `Critical injection failure`, `Mixin apply
failed`, or `MixinTransformerError` marker. Because the renderer crashed before
the resource-hash/readiness contract could run, this profile is recorded as
`FAILED` with a documented environment/loader disposition rather than as a
successful compatibility result.

Evidence hashes: raw log `9ACE1A8308F7A6283C7CEA5246046AD9AD385B99FCF5E3EFE876F7FCD1131969`;
crash report was captured under the run directory
`25690816-072434-21b12859`.

This is focused failure evidence, not proof for the complete matrix or
performance gates.
