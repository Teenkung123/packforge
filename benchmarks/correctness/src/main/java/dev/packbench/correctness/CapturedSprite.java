package dev.packbench.correctness;

/** Implemented only by the separately installed diagnostic mixin. */
public interface CapturedSprite {
    TextureCapture.Sprite correctness$snapshot();
    void correctness$captureUpload(String resourceId, int requestedLevel);
}
