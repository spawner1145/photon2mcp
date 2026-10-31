package dev.photonmcp.compat.terrain;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphTypes;
import com.lowdragmc.kilagraph.rendertype.compiler.GlslType;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderCompileContext;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderExpr;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderNode;
import com.lowdragmc.kilagraph.rendertype.compiler.StageAffinity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.lowdragmc.photon.client.shadergraph.PhotonScreenSpace;
import com.lowdragmc.photon.client.shadergraph.PhotonShaderFunctionGraph;
import com.lowdragmc.photon.client.shadergraph.ShaderGraph;

@NodeAttribute(name = "photon_mcp_terrain_depth", group = "photon_scene",
        graphTypes = {ShaderGraph.class, PhotonShaderFunctionGraph.class})
public final class TerrainDepthNode extends ShaderNode {
    public static final String SAMPLER = "SamplerTerrainDepth";
    public static final String VALID = "TerrainDepthValid";

    @Override
    public StageAffinity stageAffinity() {
        return StageAffinity.FRAGMENT_ONLY;
    }

    @Override
    public void onDefinePorts(IPortDefinitionContext context) {
        context.addInputPort("uv", RenderTypeGraphTypes.VEC2).withoutConfigurator();
        context.addOutputPort("raw", TypeHandles.FLOAT);
        context.addOutputPort("eye", TypeHandles.FLOAT);
        context.addOutputPort("visible", TypeHandles.FLOAT);
    }

    @Override
    public void compile(ShaderCompileContext context) {
        String uv = context.temp(GlslType.VEC2,
                (context.isConnected("uv") ? context.input("uv") : context.screenUv()).code()).code();
        String valid = context.useBuiltinUniform(VALID, GlslType.FLOAT);
        String sampler = context.sampler(SAMPLER).code();
        String raw = context.temp(GlslType.FLOAT,
                "(" + valid + " > 0.5 ? texture(" + sampler + ", " + uv + ").r : 1.0)").code();
        String scene = context.sampleSceneDepthRaw(new ShaderExpr(uv, GlslType.VEC2)).code();
        String projectionUv = PhotonScreenSpace.toViewportUv(context, new ShaderExpr(uv, GlslType.VEC2)).code();
        String inverseProjection = context.transformField("IProjMat", GlslType.MAT4).code();
        String eye = context.temp(GlslType.VEC4, inverseProjection + " * vec4((" + projectionUv
                + ") * 2.0 - 1.0, " + raw + " * 2.0 - 1.0, 1.0)").code();
        context.output("raw", new ShaderExpr(raw, GlslType.FLOAT));
        context.output("eye", new ShaderExpr("(-" + eye + ".z / max(abs(" + eye + ".w), 1e-8))", GlslType.FLOAT));
        context.output("visible", new ShaderExpr("(" + valid + " > 0.5 && " + raw + " < 1.0 && "
                + raw + " <= " + scene + " ? 1.0 : 0.0)", GlslType.FLOAT));
    }

    @Override
    protected String previewOutputPortId() {
        return "visible";
    }
}
