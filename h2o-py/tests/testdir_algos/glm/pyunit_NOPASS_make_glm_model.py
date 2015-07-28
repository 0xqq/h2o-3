import sys
sys.path.insert(1, "../../../")
import h2o

def pyunit_make_glm_model(ip,port):
    # TODO: PUBDEV-1717
    pros = h2o.import_frame(h2o.locate("smalldata/prostate/prostate.csv"))
    model = h2o.glm(x=pros[["AGE","DPROS","DCAPS","PSA","VOL","GLEASON"]], y=pros["CAPSULE"], family="gaussian", alpha=[0])
    new_betas = {"AGE":0.5, "DPROS":0.5, "DCAPS":0.5, "PSA":0.5, "VOL":0.5, "GLEASON":0.5}

    new_glm = model.make_glm_model(new_betas)

    names = '['
    for n in new_betas.keys(): names += "\""+n+"\","
    names = names[0:len(names)-1]+"]"
    betas = '['
    for b in new_betas.values(): betas += str(b)+","
    betas = betas[0:len(betas)-1]+"]"
    res = h2o.H2OConnection.post_json("MakeGLMModel",model=model._id,names=names,beta=betas)

if __name__ == "__main__":
    h2o.run_test(sys.argv, pyunit_make_glm_model)

