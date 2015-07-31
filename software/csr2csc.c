// Source (original may be some other place):
// http://crd-legacy.lbl.gov/~yunhe/cs267/final/source/utils/convert/matrix_io.c


/*
   converts CSR format to CSC format, not in-place,
   if a == NULL, only pattern is reorganized.
   the size of matrix is n x m.
 */

void csr2csc(int n, int m, int nz, double *a, int *col_idx, int *row_start,
             double *csc_a, int *row_idx, int *col_start)
{
  int i, j, k, l;
  int *ptr;

  for (i=0; i<=m; i++) col_start[i] = 0;

  /* determine column lengths */
  for (i=0; i<nz; i++) col_start[col_idx[i]+1]++;

  for (i=0; i<m; i++) col_start[i+1] += col_start[i];


  /* go through the structure once more. Fill in output matrix. */

  for (i=0, ptr=row_start; i<n; i++, ptr++)
    for (j=*ptr; j<*(ptr+1); j++){
      k = col_idx[j];
      l = col_start[k]++;
      row_idx[l] = i;
      if (a) csc_a[l] = a[j];
    }

  /* shift back col_start */
  for (i=m; i>0; i--) col_start[i] = col_start[i-1];

  col_start[0] = 0;
}
