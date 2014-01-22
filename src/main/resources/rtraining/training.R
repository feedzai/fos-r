library(randomForest)
library(doMC)
library(foreach)
library(foreign)
library(e1071)
library(kernlab)


########################
## DATA CONFIGURATION ##
########################

# this will be supplied at runtime
headersfile   <- ''

# this will be supplied at runtime
instancesfile <- ''

sep           <- ','

# this will be supplied at runtime
class.name = 'must supply this @ runtime'

# this will be supplied at runtime
categorical.features <- c()

# this will not be used as within pulse context all columns are meant
# to be used
ignored.columns <- c()

# this will be supplied at runtime
modelsavepath <- ''


############################
## TRAIN/TEST + BALANCING ##
############################

# This is useful when, for instance, the initial data has not enough history
# - e.g. if instances are generated with only 2010 data, the Jan-Jun data won't
# have 6 months of profile data and so will skew the model.
#
# This 'tail' should be done either in here or via outside means
pre.tail            <- NULL
train.split         <- 0.7
undersampling.ratio <- 0.5


####################
## MODEL TWEAKING ##
####################

rf.ntrees  <- 500
rf.classwt <- c(0.5, 0.5)
threshold  <- 0.5


# Register the multicore parallel backend with the foreach package
registerDoMC()

# Load model from file into variable 'model'
loadModel <- function(file) {
  cat("Loading", file, "into variable 'model'", "\n")
  load(file, envir=globalenv())
}

# Save a predictive model into file
saveModel <- function(model, file) {
  cat("Saving variable 'model' into", file, "\n")
  save(model, file=file)
}

# Save instances as an ARFF file
saveArff <- function(instances, file) {
  cat("Saving", nrow(instances), "instances into", file, "\n")
  write.arff(instances, file)
}

#
# Undersample data according to a specific column and value so that
# those datapoints become a certain percentage of the total data.
#
# Examples:
# undersample datapoints (dataframe) with fraud=0 until 60% of all datapoints are NOT FRAUD and 40% are FRAUD
# - undersample(dataframe, 'fraud', 1, 0.6)
#
# NOTE: function assumes binary classes for now
undersample <- function(data, class.name, class.value, ratio=0.9) {

  fraud_true_filter <- data[,class.name] == class.value

  fraud_true  <- data[fraud_true_filter,]
  fraud_false <- data[!fraud_true_filter,]

  under_sample_idx        <- sample(1:nrow(fraud_false), (nrow(fraud_true)/(1-ratio))-nrow(fraud_true))
  fraud_false_undersample <- fraud_false[under_sample_idx,]

  # understample
  dataset <- rbind(fraud_true, fraud_false_undersample)
  dataset[sample(nrow(dataset)),]
}

# Helper method to easily read instance data and create a named data frame.
#
# Parameters:
# - instancesfile - the path to the instances CSV file (no header).
# - headersfile - the path to the instances' header CSV file.
# - sep - the column separator to use.
#
# Returns:
# - data frame with instances
#
load.data <- function(instancesfile, headersfile, sep=',') {
  instances <- read.csv(file=instancesfile, head=FALSE, sep=sep)
  header    <- read.csv(file=headersfile, head=TRUE, sep=sep)

  if (ncol(instances) != ncol(header)) {
    stop('header does not match instances!')
  }
  colnames(instances) <- colnames(header)
  instances
}

#
# Takes the instance data and preprocesses and splits it into Training and Validation data.
#
# Parameters:
# - instances - a data frame with all the instances.
# - class.name - the name of the column with the class.
# - factorcols - a vector with the names of the columns of categorical features.
# - unusedcols - a vector with the indices of the columns that are to be ignored in the model.
# - undersampling.ratio - the ratio for undersampling the class with value 1 (binary classes only).
# - train.split - the percentage of training data to use. The rest will be used for validation.
# - pre.tail - the number of rows (tail) to use of the original data before splitting.
#              this is useful when the initial part of the data has no profile as should be discarded.
#
# Returns:
# - A list with 'train' and 'validation' data.
#
preprocess.data <- function(instances, class.name, categorical.features=c(),
                            ignored.cols=c(), undersampling.ratio=0.5, train.split=0.7,
                            pre.tail=NULL) {

  for(column in categorical.features) {
    instances[,column] <- as.factor(instances[,column])
  }

  if (!is.null(ignored.cols)) {
    instances <- instances[,-ignored.cols]
  }

  if (!is.null(pre.tail)) {
    instances <- tail(instances, n=pre.tail)
  }

  split.row = floor(nrow(instances) * train.split)

  train.data      <- instances[1:split.row,]

  train.balanced <- undersample(train.data, class.name, 1, undersampling.ratio)

  list(train=train.balanced, train.unbalanced=train.data)
}

#
# Train Random Forest model
#
# Parameters:
# - train.data - a dataframe with the training data
# - class.name - the name of the column with the class
# - ntree - number of trees in the Random Forest model
# - classwt - priors of the class
#
# Returns: the trained prediction model
#
# TODO: allow passing extra parameters to randomForest
#
train.model.rf <- function(train.data, class.name, ntree=500, classwt=c(0.5, 0.5)) {
  formula   <- as.formula(paste(class.name, "~."))
  randomForest(formula, train.data, ntree=ntree, classwt=classwt)
}


###############
## EXECUTION ##
###############

trainRmodel <- function() {
  instances = load.data(instancesfile, headersfile, sep)

  data = preprocess.data(instances=instances, class.name=class.name,
                         categorical.features=categorical.features, ignored.cols=ignored.columns,
                         undersampling.ratio=undersampling.ratio, train.split=train.split,
                         pre.tail=pre.tail)

  train.data      = data$train

  model = train.model.rf(train.data, class.name, ntree=rf.ntrees, classwt=rf.classwt)
  saveModel(model, modelsavepath)
}