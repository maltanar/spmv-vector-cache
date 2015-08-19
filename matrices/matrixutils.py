#!/usr/bin/env python

import io, numpy, scipy, struct, os
from scipy import io as ios
from copy import deepcopy
import matplotlib.pyplot as plot
import urllib, tarfile

dramBase=0x8000100


downloadDir="/home/maltanar/sandbox/spmv-vector-cache/matrices/download"
# source directory for matrices (in Matrix Market format)
localRoot="/home/maltanar/sandbox/spmv-vector-cache/matrices/mtx"
# converted matrices will be outputted here
outputBase="/home/maltanar/sandbox/spmv-vector-cache/matrices"

testSuite=["Williams/pdb1HYS", "Williams/consph", "Williams/cant", 
           "Boeing/pwtk", "Bova/rma10", "QCD/conf5_4-8x8-05", "DNVS/shipsec1", 
           "Williams/mac_econ_fwd500", "Williams/cop20k_A", 
           "Williams/webbase-1M", "Williams/mc2depi", "Hamm/scircuit"]


def getRowStarts(matrix, reverse):
  rows = matrix.shape[0]
  nnz = matrix.nnz
  seen = [0 for i in range(rows)]
  isRowStart = [0 for i in range(nnz)]
  for e in range(nnz):
    nzind = e if not reverse else (nnz-1-e)
    rowind = matrix.indices[nzind]
    if seen[rowind] == 0:
      seen[rowind] = 1
      isRowStart[nzind] = 1
  return isRowStart
  

def getMaxAliveRows(name):
  A = loadMatrix(name)
  isRowStart = getRowStarts(A, False)
  isRowEnd = getRowStarts(A, True)
  maxAlive = 0
  currentAlive = 0
  for e in range(A.nnz):
    currentAlive = currentAlive + isRowStart[e] - isRowEnd[e]
    maxAlive=max(maxAlive, currentAlive)
  return maxAlive
    

# Helper functions for getting first/last elem ind in row/col
def firstIndexIn(matrix, rowOrCol):
  return matrix.indices[matrix.indptr[rowOrCol]]
def lastIndexIn(matrix, rowOrCol):
  return matrix.indices[matrix.indptr[rowOrCol+1]-1]       

def getMaxColSpan(matrix):
  csc = loadMatrix(matrix)
  # make sure the indices are sorted
  csc.sort_indices()
  maxColSpan = 0
  for i in range(0, len(csc.indptr)-1):
      currentColSpan = lastIndexIn(csc,i) - firstIndexIn(csc,i)
      maxColSpan = max(currentColSpan, maxColSpan)
  return maxColSpan
    

# prepare all matrices in the test suite
def prepareTestSuite():
    map(lambda x: prepareUFLMatrix(x), testSuite)

# given the full name of a University of Florida matrix; download, extract and 
# convert the matrix to the form expected by the accelerator
def prepareUFLMatrix(name):
    f = urllib.URLopener()
    url="http://www.cise.ufl.edu/research/sparse/MM/"+name+".tar.gz"    
    name=name.split("/")[1]
    if not os.path.exists(downloadDir):
        os.makedirs(downloadDir)
    fileName = downloadDir+"/"+name+".tar.gz"
    # download if archive file does not exist
    if not os.path.exists(fileName):
        print "Downloading " + url
        f.retrieve(url, fileName)
    # extract if matrix market file does not exist
    if not os.path.exists(localRoot+"/"+name+".mtx")    :
        print "Extracting matrix..."
        tar = tarfile.open(fileName)
        for item in tar:
            if item.name.endswith(name+".mtx"):
                item.name = name+".mtx"
                print item.name
                tar.extract(item, localRoot)
    # convert if the destination dir doest not exist
    if not os.path.exists(outputBase+"/"+name):
        A=loadMatrix(name)
        convertMatrix(A, name)
        makeGoldenResult(A, name)
            
# example of converting data indices to another type (uint64 in this case)
def toUInt64Matrix(A):
    Ap = deepcopy(A)
    Ap.data = numpy.array(map(lambda x: np.uint64(1), A.data))
    return Ap

def makeUnitVector(A):
    return numpy.array([1 for i in range(A.shape[1])])

def makeGoldenResult(A, name):
    x=makeUnitVector(A)
    y=A*x
    f=io.open(outputBase+"/"+name+"/golden.bin", "wb")
    f.write(y)
    f.close()

# generate a histogram of row lengths
def generateRowLenHistogram(matrix):
  csr = matrix.tocsr()
  histogram = dict()
  for j in range(1, csr.shape[1]):
    currentRowLen = csr.indptr[j] - csr.indptr[j-1]
    if currentRowLen in histogram:
      histogram[currentRowLen] += 1
    else:
      histogram[currentRowLen] = 1
  return histogram

# display histogram
def showHistogram(h):
  k = h.keys()
  v = h.values()
  pos = numpy.arange(len(k))
  width = 1.0
  ax = plot.axes()
  ax.set_xticks(pos+ (width/2))
  ax.set_xticklabels(k)
  plot.bar(pos, v, width, color='r')
  plot.show()

# Make permutation matrix from row permutation vector
def makePermutationMatrixFromVector(rowpermvec):
  row_indices = range(len(rowpermvec))
  col_indices = rowpermvec
  data = [1 for x in rowpermvec]
  permMatrix = scipy.sparse.coo_matrix((data, (row_indices, col_indices)))
  return permMatrix


def permuteLongestRowFirst(matrix):
  csr = matrix.tocsr()
  # make sure the indices are sorted
  csr.sort_indices()
  # make list of last col in each row
  rowLengths = [csr.indptr[i+1]-csr.indptr[i] for i in range(csr.shape[0])]
  rowLengths = zip(rowLengths, range(csr.shape[0]))
  rowLengths.sort(reverse=True)
  # recover row indices to use as permutation vector
  permArray = [x[1] for x in rowLengths]
  return makePermutationMatrixFromVector(permArray) * csr


# load matrix from local file system (Matrix Market format file must exist
# under localRoot)
def loadMatrix(name):
  fileName=localRoot+"/"+name+".mtx"
  if os.path.exists(fileName):
    return ios.mmread(fileName).tocsc()
  else:
    print "Matrix not found! " + fileName

# increment base address by <increment> and ensure alignment to <align>
# alignment is important to burst reads from memory. since the output of this
# script decides where data lives in memory, we have to do this here.
def alignedIncrement(base, increment, align):
  res = base + increment
  rem = res % align
  if rem != 0:
    res += align-rem
  return res

def loadAndConvertMatrix(name, startAddr=dramBase):
    A=loadMatrix(name)
    return convertMatrix(A, name, startAddr)

# read in a matrix, convert it to separate CSC SpMV data files + output
# command info (for reading this from an SD card later)
def convertMatrix(A, name, startAddr=dramBase):
  if A.format != "csc":
    print "Matrix must be in CSC format! Converting.."
    A = A.tocsc()
    
  startingRow=0
  targetDir=outputBase + "/" + name
  if not os.path.exists(targetDir):
    os.makedirs(targetDir)
    
  burstAlign=64
  commands = []
  
  # metadata file: information about matrix dimensions
  fileName = targetDir + "/" + name + "-meta.bin"  
  metaDataFile = io.open(fileName, "wb")
  metaDataFile.write(struct.pack("I", A.shape[0]))
  metaDataFile.write(struct.pack("I", A.shape[1]))
  metaDataFile.write(struct.pack("I", A.nnz))
  metaDataFile.write(struct.pack("I", startingRow))
  # add command info for metadata
  commands += [(fileName.replace(outputBase, ""), startAddr)]
  # don't close the metadata file yet, we'll write the data pointers
  # to SpMV components
  fileSize = 28 # metadata is always 28 bytes (4 uint32 sizes + 3 pointers)
  startAddr = alignedIncrement(startAddr, fileSize, burstAlign)
  
  # index pointers
  # save indptr data start into metadata
  metaDataFile.write(struct.pack("I", startAddr))
  fileName = targetDir + "/" + name + "-indptr.bin"
  indPtrFile = io.open(fileName, "wb")
  indPtrFile.write(A.indptr)
  indPtrFile.close()
  # add command info
  commands += [(fileName.replace(outputBase, ""), startAddr)]
  
  # increment start address and align
  fileSize = os.path.getsize(fileName)
  startAddr = alignedIncrement(startAddr, fileSize, burstAlign)
    
  # save indices
  # save inds data start into metadata
  metaDataFile.write(struct.pack("I", startAddr))    
  fileName = targetDir + "/" + name + "-inds.bin"
  indsFile = io.open(fileName, "wb")
  indsFile.write(A.indices)
  indsFile.close()
  # create copy command
  commands += [(fileName.replace(outputBase, ""), startAddr)]  
  fileSize = os.path.getsize(fileName)  
  # align for next partition
  startAddr = alignedIncrement(startAddr, fileSize, burstAlign)
  
  # save indices
  # save elem data start into metadata
  metaDataFile.write(struct.pack("I", startAddr))    
  fileName = targetDir + "/" + name + "-data.bin"
  indsFile = io.open(fileName, "wb")
  indsFile.write(A.data)
  indsFile.close()
  # create copy command
  commands += [(fileName.replace(outputBase, ""), startAddr)]  
  fileSize = os.path.getsize(fileName)    
  
  metaDataFile.close()
  
  print "Rows = " + str(A.shape[0])
  print "Cols = " + str(A.shape[1])
  print "NonZ = " + str(A.nnz)
  
  makeUploadScript(commands, targetDir+"/upload.tcl")
  
  return [startAddr, commands]

def makeUploadScript(commands, fileName):
  cmds=map(lambda x: createCopyCommand(outputBase+x[0], x[1]), commands)
  script=reduce(lambda x,y: x+"\n"+y, cmds)
  script="connect arm hw\n"+script+"\ndisconnect 64\n"
  scriptFile = io.open(fileName, "wb")
  scriptFile.write(script)
  scriptFile.close()
  return fileName

def createCopyCommand(fileName, startAddr):
  addrString = ("0x%0.8X" % startAddr)
  return "dow -data " + fileName + " " + addrString

