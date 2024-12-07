import os

def write_file_contents_to_output(directory, output_file):
    exclude_files = {
        'package.json',
        'package-lock.json',
        'README.md',
        'tsconfig.json',
        'output.txt',
        'resume.py'
    }
    exclude_dirs = {'node_modules', 'public', '.git'}

    with open(output_file, 'w', encoding='utf-8') as outfile:
        # Walk through the directory tree and manually create a similar "tree" output
        for root, dirs, files in os.walk(directory):
            # Exclude specified directories
            dirs[:] = [d for d in dirs if d not in exclude_dirs]

            # Write the structure to the file
            level = root.replace(directory, '').count(os.sep)
            indent = ' ' * 4 * level
            outfile.write(f"{indent}{os.path.basename(root)}/\n")
            sub_indent = ' ' * 4 * (level + 1)

            for file in files:
                if file not in exclude_files:
                    outfile.write(f"{sub_indent}{file}\n")
        
        # Read and write file contents
        for root, dirs, files in os.walk(directory):
            # Exclude specified directories
            dirs[:] = [d for d in dirs if d not in exclude_dirs]

            for file in files:
                if file not in exclude_files:
                    file_path = os.path.join(root, file)
                    try:
                        with open(file_path, 'r', encoding='utf-8', errors='ignore') as infile:
                            outfile.write(f"\n\nFile: {file_path}\n\n")
                            outfile.write(infile.read())
                            outfile.write("\n\n")
                    except Exception as e:
                        print(f"Error reading {file_path}: {e}")

    print(f"Output written to: {os.path.abspath(output_file)}")


if __name__ == "__main__":
    directory_to_search = '.'  # Current directory
    output_file = 'output.txt'
    write_file_contents_to_output(directory_to_search, output_file)