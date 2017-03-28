/**
 * TiMailcore
 */

#import "TiModule.h"

@interface TiMailcoreModule : TiModule
{
}

- (void)getFolders:(id)args;
- (void)getFolderInfo:(id)args;
- (void)getMail:(id)args;
- (id)compose: (id)args;
- (id)send: (id)args;

@end
