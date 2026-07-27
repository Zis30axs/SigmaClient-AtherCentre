package com.elfmcys.yesstevemodel.client.animation.molang;

import com.elfmcys.yesstevemodel.client.compat.CompatMolangStubs;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.binding.ContextBinding;
import com.elfmcys.yesstevemodel.util.data.LazySupplier;

public class TLMBinding extends ContextBinding {

    public static final LazySupplier<TLMBinding> INSTANCE = new LazySupplier<>(TLMBinding::new);

    public TLMBinding() {
        // Upstream calls TouhouLittleMaidCompat.registerMaidAnimStates(this). The mod itself is cut
        // here, but the `tlm.*` symbols must still resolve - models reference them unconditionally
        // (e.g. 昔涟1.0.4.ysm uses `!tlm.is_sitting` in its player.main transitions), and a null
        // lookup poisons the surrounding expression. These are upstream's mod-absent values.
        CompatMolangStubs.registerTlm(this);
    }
}
