package com.teenkung.packforge.client.font;

import com.mojang.blaze3d.font.GlyphProvider;

import java.util.List;

public interface PackForgeFontSetAccess {
	void packforge$reloadPreselected(List<GlyphProvider.Conditional> providers, FontPreparedSelection selection);
}
