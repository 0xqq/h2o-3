import h2o
from . import locate


class CustomDistributionGaussian:
    def link(self, f):
        return f
    
    def linkInv(self, f):
        return f
    
    def linkInvString(self, f):    
        return f
    
    def deviance(self, w, y, f):
        return w * (y - f) * (y - f)
    
    def negHalfGradient(self, y, f):
        return y - f
    
    def initFNum(self, w, o, y):
        return w*(y-o)
    
    def initFDenom(self, w, o, y):
        return w
    
    def gammaNum(self, w, y, z, f):
        return w * z
    
    def gammaDenom(self, w, y, z, f):
        return w

class CustomDistributionGaussianWrong:
    def link(self, f):
        return f

    def linkInv(self, f):
        return f

    def linkInvString(self, f):
        return f

    def deviance(self, w, y, f):
        return w * (y - f) * (y - f)

    def negHalfGradient(self, y, f):
        return (y - f) * (y - f)

    def initFNum(self, w, o, y):
        return w*(y-o)

    def initFDenom(self, w, o, y):
        return w

    def gammaNum(self, w, y, z, f):
        return w * z

    def gammaDenom(self, w, y, z, f):
        return w



def dataset_prostate():
    df = h2o.import_file(path=locate("smalldata/prostate/prostate.csv"))
    df = df.drop("ID")
    df["CAPSULE"] = df["CAPSULE"].asfactor()
    return df.split_frame(ratios=[0.6, 0.3], seed=0)


def regression_model_distribution(ModelType, custom_distribution_func):
    (ftrain, fvalid, ftest) = dataset_prostate()
    model = ModelType(model_id="regression_custom", ntrees=3, max_depth=5,
                      score_each_iteration=True,
                      distribution="custom",
                      custom_distribution_func=custom_distribution_func)
    model.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


def regression_model_default(ModelType):
    (ftrain, fvalid, ftest) = dataset_prostate()
    model = ModelType(model_id="regression", ntrees=3, max_depth=5,
                      score_each_iteration=True,
                      distribution="gaussian")
    model.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest

