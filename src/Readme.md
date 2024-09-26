While in directory compile using

             javac -d bin -cp "libs/*" $(Get-ChildItem -Recurse -Filter *.java -Path src | ForEach-Object { $_.FullName })

and run using 
            this for the tests using junit
             
             java -cp "bin;libs/*" org.junit.runner.JUnitCore TestStubResolve [filename]
for example:

             java -cp "bin;libs/*" org.junit.runner.JUnitCore TestStubResolver


for normal:

            java -cp "bin;libs/*" [filename]


             